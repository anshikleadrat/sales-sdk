package com.leadrat.aisdk.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

public class CacheConfig {

    public static <K, V> Cache<K, V> build(int ttlMinutes) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(1_000)
                .build();
    }
}
