package com.leadrat.aisdk.query;

import com.leadrat.aisdk.config.AiSdkProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {

    private record Window(long minute, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AiSdkProperties properties;

    public RateLimiter(AiSdkProperties properties) {
        this.properties = properties;
    }

    public boolean allow(String subject) {
        int limit = properties.getQuery().getRateLimitPerMinute();
        if (limit <= 0) {
            return true;
        }
        long minute = System.currentTimeMillis() / 60_000L;
        Window window = windows.compute(subject == null ? "anonymous" : subject,
                (key, existing) -> existing == null || existing.minute() != minute
                        ? new Window(minute, new AtomicInteger(0))
                        : existing);
        return window.count().incrementAndGet() <= limit;
    }
}
