package dev.haller.dashboard.controller;

import dev.haller.dashboard.model.SettingsResponse;
import dev.haller.dashboard.service.SettingsService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public Mono<SettingsResponse> getSettings() {
        return Mono.fromCallable(settingsService::getResponse)
            .subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping
    public Mono<Void> updateSettings(@RequestBody Map<String, String> updates) {
        return Mono.fromRunnable(() -> updates.forEach(settingsService::set))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }
}
