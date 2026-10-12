package dev.haller.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.haller.dashboard.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LightService {

    private static final Logger log = LoggerFactory.getLogger(LightService.class);
    // Backstop only: dead connections are detected by TCP keepalive (see WebClientConfig).
    // Long, because a quiet bridge sends nothing and every reconnect costs a resync request.
    private static final Duration BRIDGE_IDLE_TIMEOUT = Duration.ofHours(1);

    private final WebClient webClient;
    private final WebClient hueWebClient;
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private volatile String bridgeAddress;
    // grouped_light UUID → {name, action:{on}}
    private final Map<String, ObjectNode> cachedGroups = new ConcurrentHashMap<>();
    private final Sinks.Many<Map<String, ObjectNode>> groupsSink = Sinks.many().replay().latest();
    // Guards cachedGroups and groupsSink: fetchGroups and bridge events run on different threads
    private final Object cacheLock = new Object();

    public LightService(WebClient webClient,
                        @Qualifier("hueWebClient") WebClient hueWebClient,
                        AppProperties props,
                        ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.hueWebClient = hueWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void start() {
        // Groups are loaded once the event stream is connected (see bridgeEventStream)
        discoverBridge()
            .doOnSuccess(v -> subscribeToBridgeEvents())
            .subscribe();

        Flux.interval(Duration.ofHours(24))
            .flatMap(tick -> discoverBridge())
            .subscribe();
    }

    private Mono<Void> discoverBridge() {
        return webClient.get()
            .uri(props.getLights().getDiscoveryApiUrl())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .doOnNext(data -> {
                if (data.isArray() && !data.isEmpty() && data.get(0).has("internalipaddress")) {
                    bridgeAddress = data.get(0).get("internalipaddress").asText();
                    log.info("Bridge discovered: {}", bridgeAddress);
                } else {
                    log.warn("Bridge discovery returned no bridge: {}", data);
                }
            })
            .onErrorResume(e -> {
                log.warn("Bridge discovery failed: {}", e.getMessage());
                return Mono.empty();
            })
            // Discovery can fail or come back empty: fall back to the configured address
            .then(Mono.fromRunnable(() -> {
                if (bridgeAddress == null || bridgeAddress.isEmpty()) {
                    bridgeAddress = props.getLights().getBridgeAddressFallback();
                    if (bridgeAddress == null || bridgeAddress.isEmpty()) {
                        log.error("No bridge address: discovery found none and no fallback is configured");
                    } else {
                        log.warn("Using fallback bridge address: {}", bridgeAddress);
                    }
                }
            }));
    }

    private Mono<JsonNode> getResource(String type) {
        return hueWebClient.get()
            .uri("https://" + bridgeAddress + "/clip/v2/resource/" + type)
            .header("hue-application-key", props.getLights().getApiKey())
            .retrieve()
            .bodyToMono(JsonNode.class)
            // Otherwise a failed fetch leaves the cache stale until the next reconnect
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(5))
                .filter(e -> !(e instanceof WebClientResponseException.Forbidden)));
    }

    // Rooms rarely change (room events trigger a full fetch), so a resync only needs light state
    private Mono<Void> resync() {
        boolean roomsKnown;
        synchronized (cacheLock) {
            roomsKnown = !cachedGroups.isEmpty();
        }
        return roomsKnown ? fetchGroupStates() : fetchGroups();
    }

    private Mono<Void> fetchGroupStates() {
        if (bridgeAddress == null || bridgeAddress.isEmpty()) return Mono.empty();

        return getResource("grouped_light")
            .doOnNext(groupedLights -> {
                synchronized (cacheLock) {
                    boolean changed = false;
                    for (JsonNode gl : groupedLights.path("data")) {
                        changed |= applyGroupState(gl);
                    }
                    if (changed) {
                        emitSnapshot();
                    }
                }
            })
            .onErrorResume(e -> {
                log.error("fetchGroupStates failed: {}", e.getMessage());
                return Mono.empty();
            })
            .then();
    }

    private Mono<Void> fetchGroups() {
        if (bridgeAddress == null || bridgeAddress.isEmpty()) return Mono.empty();

        return Mono.zip(getResource("room"), getResource("grouped_light"))
            .doOnNext(tuple -> {
                JsonNode rooms = tuple.getT1();
                JsonNode groupedLights = tuple.getT2();

                record GlState(boolean on, double brightness) {}
                Map<String, GlState> glStateById = new HashMap<>();
                if (groupedLights.has("data")) {
                    for (JsonNode gl : groupedLights.get("data")) {
                        boolean on = gl.path("on").path("on").asBoolean(false);
                        double brightness = gl.path("dimming").path("brightness").asDouble(100.0);
                        glStateById.put(gl.path("id").asText(), new GlState(on, brightness));
                    }
                }

                Map<String, ObjectNode> merged = new HashMap<>();
                if (rooms.has("data")) {
                    for (JsonNode room : rooms.get("data")) {
                        String name = room.path("metadata").path("name").asText("Unknown");
                        String glId = findGroupedLightId(room);
                        if (glId == null) continue;
                        GlState state = glStateById.getOrDefault(glId, new GlState(false, 100.0));
                        merged.put(glId, buildGroupNode(name, state.on(), state.brightness()));
                    }
                }

                synchronized (cacheLock) {
                    cachedGroups.clear();
                    cachedGroups.putAll(merged);
                    emitSnapshot();
                }
                log.info("Loaded {} rooms", merged.size());
            })
            .onErrorResume(e -> {
                log.error("fetchGroups failed: {}", e.getMessage());
                return Mono.empty();
            })
            .then();
    }

    private void subscribeToBridgeEvents() {
        if (bridgeAddress == null || bridgeAddress.isEmpty()) return;

        // defer: every (re)connect builds the URL from the current bridgeAddress
        Flux.defer(this::bridgeEventStream)
            .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5))
                .maxBackoff(Duration.ofSeconds(60))
                .transientErrors(true)
                .filter(e -> !(e instanceof WebClientResponseException.Forbidden))
                .doBeforeRetry(sig -> log.warn("Bridge SSE reconnecting: {}", sig.failure().toString())))
            .subscribe(this::processBridgeEvent,
                e -> log.error("Bridge SSE stopped permanently: {}", e.getMessage()));
    }

    private Flux<String> bridgeEventStream() {
        String address = bridgeAddress;
        if (address == null || address.isEmpty()) {
            return Flux.error(new IllegalStateException("Bridge address unknown"));
        }
        String url = "https://" + address + "/eventstream/clip/v2";
        log.info("Connecting to bridge SSE event stream at {}", url);

        return hueWebClient.get()
            .uri(url)
            .header("hue-application-key", props.getLights().getApiKey())
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchangeToFlux(response -> {
                if (response.statusCode().isError()) {
                    return response.<String>createError().flux();
                }
                // Stream is open: resync the cache to pick up changes missed while disconnected
                resync().subscribe();
                return response.bodyToFlux(String.class);
            })
            // A half-open connection never errors, so a silent stream is reconnected
            .timeout(BRIDGE_IDLE_TIMEOUT)
            // retryWhen only reacts to errors, so a cleanly closed stream must become one
            .concatWith(Flux.error(new IllegalStateException("Bridge SSE stream closed")));
    }

    private void processBridgeEvent(String eventData) {
        if (eventData == null || eventData.isBlank()) return;
        try {
            JsonNode events = objectMapper.readTree(eventData);
            if (!events.isArray()) return;
            boolean roomsChanged;
            synchronized (cacheLock) {
                roomsChanged = applyBridgeEvents(events);
            }
            if (roomsChanged) {
                log.info("Room added, removed or renamed, reloading rooms");
                fetchGroups().subscribe();
            }
        } catch (Exception e) {
            log.error("Failed to parse bridge event: {}", e.getMessage());
        }
    }

    // Caller must hold cacheLock. Returns true if rooms changed and must be reloaded.
    private boolean applyBridgeEvents(JsonNode events) {
        boolean changed = false;
        boolean roomsChanged = false;
        for (JsonNode event : events) {
            String eventType = event.path("type").asText();
            for (JsonNode resource : event.path("data")) {
                String resourceType = resource.path("type").asText();
                if ("room".equals(resourceType)
                        && ("add".equals(eventType) || "delete".equals(eventType) || resource.has("metadata"))) {
                    roomsChanged = true;
                } else if ("update".equals(eventType) && "grouped_light".equals(resourceType)) {
                    changed |= applyGroupState(resource);
                }
            }
        }
        if (changed) {
            emitSnapshot();
        }
        return roomsChanged;
    }

    // Caller must hold cacheLock. Applies on/dimming from a (partial) grouped_light resource.
    private boolean applyGroupState(JsonNode resource) {
        ObjectNode cached = cachedGroups.get(resource.path("id").asText());
        if (cached == null) return false;
        ObjectNode action = (ObjectNode) cached.get("action");
        boolean changed = false;
        if (resource.has("on")) {
            boolean on = resource.path("on").path("on").asBoolean();
            changed |= action.path("on").asBoolean() != on;
            action.put("on", on);
        }
        if (resource.has("dimming")) {
            double brightness = resource.path("dimming").path("brightness").asDouble(100.0);
            changed |= action.path("brightness").asDouble() != brightness;
            action.put("brightness", brightness);
        }
        return changed;
    }

    // Caller must hold cacheLock. Deep copy, so later in-place updates don't race with serialization.
    private void emitSnapshot() {
        Map<String, ObjectNode> snapshot = new HashMap<>();
        cachedGroups.forEach((id, node) -> snapshot.put(id, node.deepCopy()));
        Sinks.EmitResult result = groupsSink.tryEmitNext(snapshot);
        if (result.isFailure()) {
            log.warn("Failed to emit light state: {}", result);
        }
    }

    public Mono<JsonNode> triggerLight(String groupedLightId, boolean on, Double brightness) {
        String url = "https://" + bridgeAddress + "/clip/v2/resource/grouped_light/" + groupedLightId;
        Map<String, Object> body = new HashMap<>();
        body.put("on", Map.of("on", on));
        if (brightness != null) {
            body.put("dimming", Map.of("brightness", brightness));
        }
        return hueWebClient.put()
            .uri(url)
            .header("hue-application-key", props.getLights().getApiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .onErrorResume(e -> {
                log.error("triggerLight failed: {}", e.getMessage());
                return Mono.empty();
            });
    }

    public Flux<ServerSentEvent<String>> eventFlux() {
        Flux<ServerSentEvent<String>> groupsFlux = Flux.concat(
            groupsSink.asFlux().next(),
            Flux.interval(Duration.ofHours(1)).flatMap(tick -> groupsSink.asFlux().next())
        ).map(data -> buildEvent("groups", data));

        Flux<ServerSentEvent<String>> lightFlux = groupsSink.asFlux()
            .map(data -> buildEvent("light", data));

        return Flux.merge(groupsFlux, lightFlux);
    }

    private String findGroupedLightId(JsonNode room) {
        for (JsonNode svc : room.path("services")) {
            if ("grouped_light".equals(svc.path("rtype").asText())) {
                return svc.path("rid").asText();
            }
        }
        return null;
    }

    private ObjectNode buildGroupNode(String name, boolean on, double brightness) {
        ObjectNode action = objectMapper.createObjectNode()
            .put("on", on)
            .put("brightness", brightness);
        return objectMapper.createObjectNode()
            .<ObjectNode>put("name", name)
            .set("action", action);
    }

    private ServerSentEvent<String> buildEvent(String eventName, Map<String, ObjectNode> data) {
        try {
            return ServerSentEvent.<String>builder()
                .event(eventName)
                .data(objectMapper.writeValueAsString(data))
                .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
