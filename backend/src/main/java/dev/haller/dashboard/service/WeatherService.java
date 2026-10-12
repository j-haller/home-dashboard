package dev.haller.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.haller.dashboard.config.AppProperties;
import dev.haller.dashboard.model.WeatherData;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WebClient webClient;
    private final AppProperties props;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<WeatherData> dataSink = Sinks.many().replay().latest();

    public WeatherService(WebClient webClient, AppProperties props, SettingsService settingsService, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.props = props;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void startPolling() {
        Flux.merge(
                Flux.interval(Duration.ZERO, Duration.ofMinutes(15)).cast(Object.class),
                settingsService.changes()
                    .filter(k -> k.startsWith("weather.") || "latitude".equals(k) || "longitude".equals(k))
                    .cast(Object.class)
            )
            .filter(ignored -> settingsService.isEnabled("weather"))
            .flatMap(ignored -> fetchData())
            .subscribe(
                data -> {
                    log.info("Weather data updated: {}°C, code={}", data.current().temperature(), data.current().weatherCode());
                    dataSink.tryEmitNext(data);
                },
                err -> log.error("Weather polling error", err)
            );
    }

    private Mono<WeatherData> fetchData() {
        AppProperties.Weather w = props.getWeather();
        String url = w.getApiUrl()
            + "?latitude=" + settingsService.getLatitude()
            + "&longitude=" + settingsService.getLongitude()
            + "&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,relative_humidity_2m,is_day"
            + "&hourly=temperature_2m,apparent_temperature,precipitation_probability,weather_code"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max"
            + "&timezone=auto&forecast_days=7";

        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::processResponse)
            .doOnError(e -> log.error("Weather fetch failed: {}", e.getMessage()))
            .onErrorReturn(new WeatherData(
                new WeatherData.CurrentWeather(0, 0, 0, 0, 0, 0, 1),
                List.of(), List.of()
            ));
    }

    private WeatherData processResponse(JsonNode root) {
        JsonNode curr = root.path("current");
        WeatherData.CurrentWeather current = new WeatherData.CurrentWeather(
            curr.path("temperature_2m").asDouble(),
            curr.path("apparent_temperature").asDouble(),
            curr.path("precipitation").asDouble(),
            curr.path("weather_code").asInt(),
            curr.path("wind_speed_10m").asDouble(),
            curr.path("relative_humidity_2m").asInt(),
            curr.path("is_day").asInt()
        );

        String today = LocalDate.now().toString();
        JsonNode hourlyNode = root.path("hourly");
        JsonNode times = hourlyNode.path("time");
        JsonNode temps = hourlyNode.path("temperature_2m");
        JsonNode apparent = hourlyNode.path("apparent_temperature");
        JsonNode precipProb = hourlyNode.path("precipitation_probability");
        JsonNode codes = hourlyNode.path("weather_code");

        List<WeatherData.HourlyEntry> hourly = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            String time = times.get(i).asText();
            if (time.startsWith(today)) {
                hourly.add(new WeatherData.HourlyEntry(
                    time,
                    temps.get(i).asDouble(),
                    apparent.get(i).asDouble(),
                    precipProb.get(i).asInt(),
                    codes.get(i).asInt()
                ));
            }
        }

        JsonNode dailyNode = root.path("daily");
        JsonNode dates = dailyNode.path("time");
        JsonNode dailyCodes = dailyNode.path("weather_code");
        JsonNode maxTemps = dailyNode.path("temperature_2m_max");
        JsonNode minTemps = dailyNode.path("temperature_2m_min");
        JsonNode precipSums = dailyNode.path("precipitation_sum");
        JsonNode precipProbs = dailyNode.path("precipitation_probability_max");

        List<WeatherData.DailyEntry> daily = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            daily.add(new WeatherData.DailyEntry(
                dates.get(i).asText(),
                dailyCodes.get(i).asInt(),
                maxTemps.get(i).asDouble(),
                minTemps.get(i).asDouble(),
                precipSums.get(i).asDouble(),
                precipProbs.get(i).asInt()
            ));
        }

        return new WeatherData(current, hourly, daily);
    }

    public Flux<ServerSentEvent<String>> eventFlux() {
        return dataSink.asFlux()
            .filter(data -> settingsService.isEnabled("weather"))
            .map(data -> {
                try {
                    return ServerSentEvent.<String>builder()
                        .event("weather")
                        .data(objectMapper.writeValueAsString(data))
                        .build();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}
