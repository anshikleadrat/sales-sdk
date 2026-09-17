package com.leadrat.aisdk.query;

import com.github.benmanes.caffeine.cache.Cache;
import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.config.CacheConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

public class QueryCache {

    private final Cache<String, QueryResponse> cache;

    public QueryCache(AiSdkProperties properties) {
        this.cache = CacheConfig.build(properties.getQuery().getCacheTtlMinutes());
    }

    public String key(QueryRequest request, EffectivePlan plan, long configVersion) {
        String targets = request.targets().stream()
                .map(t -> t.entity() + ":" + t.id())
                .sorted()
                .collect(Collectors.joining(","));
        String raw = String.join("|",
                String.valueOf(configVersion),
                request.question() == null ? "" : request.question().trim(),
                targets,
                String.valueOf(plan.parentDepth()),
                String.valueOf(plan.childDepth()),
                String.valueOf(plan.maxChildrenPerRelation()),
                String.join(",", sorted(plan.allowedRelations())));
        return hash(raw);
    }

    public QueryResponse get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, QueryResponse response) {
        cache.put(key, response);
    }

    private List<String> sorted(List<String> values) {
        return values.stream().sorted().toList();
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(raw.hashCode());
        }
    }
}
