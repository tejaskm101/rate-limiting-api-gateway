package com.example.RateLimitingAPIGateway.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int refillRate;

    public Client() {
    }

    public Client(String apiKey, int capacity, int refillRate) {
        this.apiKey = apiKey;
        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    public Long getId() {
        return id;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRefillRate() {
        return refillRate;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void setRefillRate(int refillRate) {
        this.refillRate = refillRate;
    }
}
