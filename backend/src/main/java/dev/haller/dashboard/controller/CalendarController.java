package dev.haller.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final WebClient webClient;

    public CalendarController(WebClient webClient) {
        this.webClient = webClient;
    }

    @GetMapping("/regions")
    public Mono<String> regions() {
        return webClient.get()
            .uri("https://openerz.metaodi.ch/api/parameter/regions")
            .retrieve()
            .bodyToMono(String.class);
    }

    @GetMapping("/areas")
    public Mono<String> areas(@RequestParam String region) {
        return webClient.get()
            .uri("https://openerz.metaodi.ch/api/parameter/areas?region={region}", region)
            .retrieve()
            .bodyToMono(String.class);
    }

    @GetMapping("/types")
    public Mono<String> types(@RequestParam String region) {
        return webClient.get()
            .uri("https://openerz.metaodi.ch/api/parameter/types?region={region}", region)
            .retrieve()
            .bodyToMono(String.class);
    }
}
