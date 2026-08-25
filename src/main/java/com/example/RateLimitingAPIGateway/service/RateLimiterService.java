package com.example.RateLimitingAPIGateway.service;

import com.example.RateLimitingAPIGateway.entity.Client;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimiterService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(Client client) {

        String baseKey = "rate_limit:" + client.getApiKey();

        String tokenKey = baseKey + ":tokens";
        String timeKey = baseKey + ":lastRefill";

        long now = System.currentTimeMillis();

        Object tokensObject = redisTemplate.opsForValue().get(tokenKey);
        Object lastRefillObject = redisTemplate.opsForValue().get(timeKey);

        int tokens;
        long lastRefill;

        if (tokensObject == null || lastRefillObject == null) {
            tokens = client.getCapacity();
            lastRefill = now;
        } else {
            tokens = Integer.parseInt(tokensObject.toString());
            lastRefill = Long.parseLong(lastRefillObject.toString());
        }

        // Calculate tokens to add based on elapsed time
        long elapsedTime = now - lastRefill;

        int refillRate = client.getRefillRate();

        int tokensToAdd = (int) ((elapsedTime * refillRate) / 60_000);

        if (tokensToAdd > 0) {
            tokens = Math.min(
                    client.getCapacity(),
                    tokens + tokensToAdd
            );

            lastRefill = now;
        }

        if (tokens <= 0) {
            redisTemplate.opsForValue().set(tokenKey, String.valueOf(tokens));
            redisTemplate.opsForValue().set(timeKey, String.valueOf(lastRefill));

            return false;
        }

        // Consume one token
        tokens--;

        redisTemplate.opsForValue().set(tokenKey, String.valueOf(tokens));
        redisTemplate.opsForValue().set(timeKey, String.valueOf(lastRefill));

        return true;
    }
}