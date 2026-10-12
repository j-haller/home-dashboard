package dev.haller.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.haller.dashboard.model.CalendarData;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CalendarService {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    private static final Map<String, String> GERMAN_MONTHS = Map.ofEntries(
        Map.entry("Januar", "01"), Map.entry("Februar", "02"), Map.entry("März", "03"),
        Map.entry("April", "04"), Map.entry("Mai", "05"), Map.entry("Juni", "06"),
        Map.entry("Juli", "07"), Map.entry("August", "08"), Map.entry("September", "09"),
        Map.entry("Oktober", "10"), Map.entry("November", "11"), Map.entry("Dezember", "12")
    );

    private final WebClient webClient;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<CalendarData> dataSink = Sinks.many().replay().latest();

    public CalendarService(WebClient webClient, SettingsService settingsService, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void startPolling() {
        Flux.merge(
                Flux.interval(Duration.ZERO, Duration.ofHours(1)).cast(Object.class),
                settingsService.changes().filter(k -> k.startsWith("calendar.")).cast(Object.class)
            )
            .filter(ignored -> settingsService.isEnabled("calendar"))
            .flatMap(ignored -> fetchData())
            .subscribe(
                data -> {
                    log.info("Calendar data updated: types={}, mrgreen={}",
                        data.wasteTypes().keySet(), data.mrgreen().size());
                    dataSink.tryEmitNext(data);
                },
                err -> log.error("Calendar polling error", err)
            );
    }

    private Mono<JsonNode> fetchOpenErz(String region, String area, List<String> types, String today) {
        StringBuilder sb = new StringBuilder("https://openerz.metaodi.ch/api/calendar.json?");
        sb.append("start=").append(today);
        sb.append("&sort=date&offset=0");
        if (!region.isEmpty()) sb.append("&region=").append(enc(region));
        if (!area.isEmpty()) sb.append("&area=").append(enc(area));
        for (String type : types) sb.append("&types=").append(enc(type));

        return webClient.get()
            .uri(sb.toString())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .doOnError(e -> log.error("OpenERZ fetch failed: {}", e.getMessage()))
            .onErrorReturn(objectMapper.createObjectNode());
    }

    private Mono<JsonNode> fetchMrGreen(String zip, String type) {
        return webClient.post()
            .uri("https://api-service.mr-green.ch/api/system/pickup-dates?zip=" + enc(zip) + "&type=" + enc(type))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .doOnError(e -> log.error("Mr. Green fetch failed: {}", e.getMessage()))
            .onErrorReturn(objectMapper.createObjectNode());
    }

    private Mono<CalendarData> fetchData() {
        String region = settingsService.getCalendarRegion();
        String area = settingsService.getCalendarArea();
        List<String> types = settingsService.getCalendarTypesList();
        int limit = parseLimit(settingsService.getCalendarLimit());
        String today = LocalDate.now().toString();

        Mono<JsonNode> erzMono = fetchOpenErz(region, area, types, today);

        if (settingsService.isCalendarMrgreenEnabled()) {
            String zip = settingsService.getCalendarMrgreenZip();
            String mrType = settingsService.getCalendarMrgreenType();
            return Mono.zip(erzMono, fetchMrGreen(zip, mrType))
                .map(t -> new CalendarData(processErz(t.getT1(), limit), processMrGreen(t.getT2(), limit)))
                .doOnError(e -> log.error("Calendar processing error: {}", e.getMessage()))
                .onErrorReturn(new CalendarData(Map.of(), List.of()));
        } else {
            return erzMono
                .map(erz -> new CalendarData(processErz(erz, limit), List.of()))
                .doOnError(e -> log.error("Calendar processing error: {}", e.getMessage()))
                .onErrorReturn(new CalendarData(Map.of(), List.of()));
        }
    }

    private Map<String, List<String>> processErz(JsonNode erz, int limit) {
        Map<String, List<String>> wasteTypes = new LinkedHashMap<>();
        JsonNode result = erz.get("result");
        if (result != null && result.isArray()) {
            result.forEach(item -> {
                String type = item.path("waste_type").asText();
                String date = item.path("date").asText();
                List<String> dates = wasteTypes.computeIfAbsent(type, k -> new ArrayList<>());
                if (dates.size() < limit) dates.add(date);
            });
        } else {
            log.warn("OpenERZ result missing or not an array");
        }
        return wasteTypes;
    }

    private List<String> processMrGreen(JsonNode mrGreen, int limit) {
        List<String> dates = new ArrayList<>();
        JsonNode datesData = mrGreen.path("data");
        if (datesData.isArray() && !datesData.isEmpty()) {
            JsonNode datesNode = datesData.get(0).path("date");
            if (datesNode.isArray()) {
                datesNode.forEach(d -> {
                    if (dates.size() >= limit) return;
                    try {
                        dates.add(convertGermanDate(d.asText()));
                    } catch (Exception e) {
                        log.warn("Failed to parse Mr. Green date '{}': {}", d.asText(), e.getMessage());
                    }
                });
            }
        } else {
            log.warn("Mr. Green dates_data missing or empty");
        }
        return dates;
    }

    private String convertGermanDate(String dateStr) {
        String[] parts = dateStr.trim().split("\\s+");
        String day = parts[0].replace(".", "").strip();
        String paddedDay = day.length() == 1 ? "0" + day : day;
        String monthNum = GERMAN_MONTHS.getOrDefault(parts[1], "01");
        String year = parts[2].strip();
        return year + "-" + monthNum + "-" + paddedDay;
    }

    private static int parseLimit(String s) {
        try { return Math.max(1, Integer.parseInt(s)); }
        catch (NumberFormatException e) { return 10; }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public Flux<ServerSentEvent<String>> eventFlux() {
        return dataSink.asFlux()
            .filter(data -> settingsService.isEnabled("calendar"))
            .map(data -> {
                try {
                    return ServerSentEvent.<String>builder()
                        .event("calendar")
                        .data(objectMapper.writeValueAsString(data))
                        .build();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
