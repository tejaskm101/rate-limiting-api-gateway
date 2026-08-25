package com.example.RateLimitingAPIGateway.service;

import com.example.RateLimitingAPIGateway.entity.Client;
import com.example.RateLimitingAPIGateway.repository.ClientRepository;
import org.springframework.stereotype.Service;

@Service
public class ClientIdentificationService {

    private final ClientRepository clientRepository;

    public ClientIdentificationService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Client identifyClient(String apiKey) {
        return clientRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new RuntimeException("Invalid API key"));
    }
}
