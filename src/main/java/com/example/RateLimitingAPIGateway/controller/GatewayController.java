package com.example.RateLimitingAPIGateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GatewayController {
    @GetMapping("/api/test")
    public String test() {
        return "Gateway is working!";
    }
}
