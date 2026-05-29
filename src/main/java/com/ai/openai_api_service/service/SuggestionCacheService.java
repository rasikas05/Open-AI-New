package com.ai.openai_api_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SuggestionCacheService {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${suggestion.cache.ttl-seconds:900}")
    private long ttlSeconds;

    @Value("${suggestion.cache.max-entries:1000}")
    private int maxEntries;

    public List<String> get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return List.of();
        }
        if (entry.expiresAtEpochSeconds < Instant.now().getEpochSecond()) {
            cache.remove(key);
            return List.of();
        }
        return new ArrayList<>(entry.suggestions);
    }

    public void put(String key, List<String> suggestions) {
        if (key == null || key.isBlank() || suggestions == null || suggestions.isEmpty()) {
            return;
        }
        if (cache.size() >= maxEntries) {
            evictExpiredEntries();
            if (cache.size() >= maxEntries) {
                String firstKey = cache.keySet().stream().findFirst().orElse(null);
                if (firstKey != null) {
                    cache.remove(firstKey);
                }
            }
        }
        long expiresAt = Instant.now().getEpochSecond() + Math.max(60, ttlSeconds);
        cache.put(key, new CacheEntry(new ArrayList<>(suggestions), expiresAt));
    }

    private void evictExpiredEntries() {
        long now = Instant.now().getEpochSecond();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochSeconds < now);
    }

    private static class CacheEntry {
        private final List<String> suggestions;
        private final long expiresAtEpochSeconds;

        private CacheEntry(List<String> suggestions, long expiresAtEpochSeconds) {
            this.suggestions = suggestions;
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        }
    }
}
