package com.example.RateLimitingAPIGateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class GatewayController {

    private final RestClient restClient;

    public GatewayController() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .build();
    }

    @GetMapping("/api/backend")
    public String forwardToBackend() {
        return restClient.get()
                .uri("/backend/test")
                .retrieve()
                .body(String.class);
    }
}
