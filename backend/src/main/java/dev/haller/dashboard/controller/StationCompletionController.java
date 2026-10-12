package dev.haller.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
public class StationCompletionController {

    private final WebClient webClient;

    public StationCompletionController(WebClient webClient) {
        this.webClient = webClient;
    }

    @GetMapping("/api/station-completion")
    public Mono<String> stationCompletion(@RequestParam String term) {
        return webClient.get()
            .uri("https://search.ch/timetable/api/completion.json?term={term}", term)
            .retrieve()
            .bodyToMono(String.class);
    }

    @GetMapping("/api/town-completion")
    public Mono<String> townCompletion(@RequestParam String term) {
        return webClient.get()
            .uri("https://search.ch/meteo/api/expandtown?term={term}", term)
            .retrieve()
            .bodyToMono(String.class);
    }

    @GetMapping("/api/station-location")
    public Mono<String> stationLocation(@RequestParam String stop) {
        return webClient.get()
            .uri("https://search.ch/timetable/api/station.json?stop={stop}", stop)
            .retrieve()
            .bodyToMono(String.class);
    }
}
