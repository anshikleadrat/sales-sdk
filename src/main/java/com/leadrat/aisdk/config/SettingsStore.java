package com.leadrat.aisdk.config;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsStore {

    private final JdbcTemplate jdbc;

    public SettingsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, String> all() {
        Map<String, String> values = new LinkedHashMap<>();
        jdbc.query("SELECT name, value FROM sdk_setting", rs -> {
            values.put(rs.getString(1), rs.getString(2));
        });
        return values;
    }

    public void put(String name, String value) {
        jdbc.update("""
                INSERT INTO sdk_setting (name, value, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at""",
                name, value, Instant.now().toString());
    }
}
