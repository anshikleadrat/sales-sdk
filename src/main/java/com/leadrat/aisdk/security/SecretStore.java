package com.leadrat.aisdk.security;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.function.Supplier;

public class SecretStore {

    private final JdbcTemplate jdbc;

    public SecretStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized String getOrCreate(String name, Supplier<String> generator) {
        String existing = find(name);
        if (existing != null) {
            return existing;
        }
        jdbc.update("INSERT OR IGNORE INTO sdk_secret (name, value, created_at) VALUES (?, ?, ?)",
                name, generator.get(), Instant.now().toString());
        return find(name);
    }

    private String find(String name) {
        return jdbc.query("SELECT value FROM sdk_secret WHERE name = ?", (rs, i) -> rs.getString(1), name)
                .stream().findFirst().orElse(null);
    }
}
