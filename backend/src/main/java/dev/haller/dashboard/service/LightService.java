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

    private final WebClient webClient;
    private final WebClient hueWebClient;
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private volatile String bridgeAddress;
    // grouped_light UUID → {name, action:{on}}
    private final Map<String, ObjectNode> cachedGroups = new ConcurrentHashMap<>();
    private final Sinks.Many<Map<String, ObjectNode>> groupsSink = Sinks.many().replay().latest();

    private final SettingsService settingsService;

    public LightService(WebClient webClient,
                        @Qualifier("hueWebClient") WebClient hueWebClient,
                        AppProperties props,
                        SettingsService settingsService,
                        ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.hueWebClient = hueWebClient;
        this.props = props;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void start() {
        discoverBridge()
            .doOnSuccess(v -> {
                fetchGroups().subscribe();
                subscribeToBridgeEvents();
            })
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
                }
            })
            .onErrorResume(e -> {
                bridgeAddress = props.getLights().getBridgeAddressFallback();
                log.warn("Bridge discovery failed, using fallback: {}", bridgeAddress);
                return Mono.empty();
            })
            .then();
    }

    private Mono<Void> fetchGroups() {
        if (bridgeAddress == null || bridgeAddress.isEmpty()) return Mono.empty();
        String apiKey = props.getLights().getApiKey();

        Mono<JsonNode> roomsMono = hueWebClient.get()
            .uri("https://" + bridgeAddress + "/clip/v2/resource/room")
            .header("hue-application-key", apiKey)
            .retrieve()
            .bodyToMono(JsonNode.class);

        Mono<JsonNode> groupedLightsMono = hueWebClient.get()
            .uri("https://" + bridgeAddress + "/clip/v2/resource/grouped_light")
            .header("hue-application-key", apiKey)
            .retrieve()
            .bodyToMono(JsonNode.class);

        return Mono.zip(roomsMono, groupedLightsMono)
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

                cachedGroups.clear();
                cachedGroups.putAll(merged);
                groupsSink.tryEmitNext(new HashMap<>(cachedGroups));
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
        String url = "https://" + bridgeAddress + "/eventstream/clip/v2";
        String apiKey = props.getLights().getApiKey();

        hueWebClient.get()
            .uri(url)
            .header("hue-application-key", apiKey)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .retrieve()
            .bodyToFlux(String.class)
            .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5))
                .maxBackoff(Duration.ofSeconds(60))
                .filter(e -> !(e instanceof org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden))
                .doBeforeRetry(sig -> log.warn("Bridge SSE reconnecting: {}", sig.failure().getMessage())))
            .subscribe(this::processBridgeEvent);

        log.info("Subscribed to bridge SSE event stream at {}", url);
    }

    private void processBridgeEvent(String eventData) {
        if (eventData == null || eventData.isBlank()) return;
        try {
            JsonNode events = objectMapper.readTree(eventData);
            if (!events.isArray()) return;
            boolean changed = false;
            for (JsonNode event : events) {
                if (!"update".equals(event.path("type").asText())) continue;
                for (JsonNode resource : event.path("data")) {
                    if (!"grouped_light".equals(resource.path("type").asText())) continue;
                    String id = resource.path("id").asText();
                    ObjectNode cached = cachedGroups.get(id);
                    if (cached == null) continue;
                    ObjectNode action = (ObjectNode) cached.get("action");
                    if (resource.has("on")) {
                        action.put("on", resource.path("on").path("on").asBoolean());
                        changed = true;
                    }
                    if (resource.has("dimming")) {
                        action.put("brightness", resource.path("dimming").path("brightness").asDouble(100.0));
                        changed = true;
                    }
                }
            }
            if (changed) {
                groupsSink.tryEmitNext(new HashMap<>(cachedGroups));
            }
        } catch (Exception e) {
            log.error("Failed to parse bridge event: {}", e.getMessage());
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
        if (!settingsService.isEnabled("lights")) return Flux.empty();
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
