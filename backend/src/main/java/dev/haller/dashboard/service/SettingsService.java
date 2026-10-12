package dev.haller.dashboard.service;

import dev.haller.dashboard.config.AppProperties;
import dev.haller.dashboard.model.SettingsResponse;
import dev.haller.dashboard.repository.SettingsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SettingsService {

    private final SettingsRepository repository;
    private final AppProperties props;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Sinks.Many<String> changeSink = Sinks.many().multicast().onBackpressureBuffer();

    public SettingsService(SettingsRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @PostConstruct
    void loadCache() {
        cache.putAll(repository.getAll());
    }

    public String getStation() {
        return cache.getOrDefault("station", props.getStationboard().getStation());
    }

    public String getZip() {
        return cache.getOrDefault("zip", props.getCalendar().getZip());
    }

    public String getLatitude() {
        return cache.getOrDefault("latitude", props.getWeather().getLatitude());
    }

    public String getLongitude() {
        return cache.getOrDefault("longitude", props.getWeather().getLongitude());
    }

    public String getWeatherLocation() {
        return cache.getOrDefault("weather.location", "");
    }

    public String getCalendarRegion() {
        return cache.getOrDefault("calendar.region", "");
    }

    public String getCalendarArea() {
        return cache.getOrDefault("calendar.area", "");
    }

    public List<String> getCalendarTypesList() {
        String raw = cache.getOrDefault("calendar.types", "cardboard,paper");
        return raw.isBlank() ? List.of() :
            Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public String getCalendarLimit() {
        return cache.getOrDefault("calendar.limit", "10");
    }

    public boolean isCalendarMrgreenEnabled() {
        return !"false".equals(cache.get("calendar.mrgreen.enabled"));
    }

    public String getCalendarMrgreenZip() {
        String zip = cache.getOrDefault("calendar.mrgreen.zip", "");
        return zip.isEmpty() ? getZip() : zip;
    }

    public String getCalendarMrgreenType() {
        return cache.getOrDefault("calendar.mrgreen.type", "monthly");
    }

    public boolean isEnabled(String service) {
        return !"false".equals(cache.get(service + ".enabled"));
    }

    public SettingsResponse getResponse() {
        return new SettingsResponse(
            getStation(), getZip(), getLatitude(), getLongitude(), getWeatherLocation(),
            getCalendarRegion(), getCalendarArea(), getCalendarTypesList(), getCalendarLimit(),
            isCalendarMrgreenEnabled(), getCalendarMrgreenZip(), getCalendarMrgreenType(),
            isEnabled("stationboard"), isEnabled("weather"),
            isEnabled("calendar"), isEnabled("wifi"), isEnabled("lights")
        );
    }

    public Flux<String> changes() {
        return changeSink.asFlux();
    }

    public void set(String key, String value) {
        repository.set(key, value);
        cache.put(key, value);
        changeSink.tryEmitNext(key);
    }
}
