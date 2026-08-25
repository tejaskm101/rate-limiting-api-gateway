package com.example.RateLimitingAPIGateway.controller;

import com.example.RateLimitingAPIGateway.entity.Client;
import com.example.RateLimitingAPIGateway.service.ClientIdentificationService;
import com.example.RateLimitingAPIGateway.service.RateLimiterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.http.ResponseEntity;

@RestController
public class GatewayController {

    private final RestClient restClient;
    private final ClientIdentificationService clientIdentificationService;
    private final RateLimiterService rateLimiterService;

    public GatewayController(
            ClientIdentificationService clientIdentificationService,
            RateLimiterService rateLimiterService) {

        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .build();

        this.clientIdentificationService = clientIdentificationService;
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/api/backend")
    public ResponseEntity<String> forwardToBackend(
            @RequestHeader("X-API-Key") String apiKey) {

        Client client = clientIdentificationService.identifyClient(apiKey);

        if (!rateLimiterService.isAllowed(client)) {
            return ResponseEntity
                    .status(429)
                    .body("Too Many Requests");
        }

        String response = restClient.get()
                .uri("/backend/test")
                .header("X-Client-Id", client.getApiKey())
                .retrieve()
                .body(String.class);

        return ResponseEntity.ok(response);
    }
}