package com.leadrat.aisdk.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;

public class PasswordStore {

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public PasswordStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isSetupCompleted() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_setup WHERE id = 1", Integer.class);
        return count != null && count > 0;
    }

    public void store(String rawPassword) {
        jdbc.update("INSERT INTO admin_setup (id, password_hash, setup_completed_at) VALUES (1, ?, ?)",
                encoder.encode(rawPassword), Instant.now().toString());
    }

    public void ensurePassword(String rawPassword) {
        if (isSetupCompleted() && matches(rawPassword)) {
            return;
        }
        jdbc.update("""
            INSERT INTO admin_setup (id, password_hash, setup_completed_at) VALUES (1, ?, ?)
            ON CONFLICT(id) DO UPDATE SET password_hash = excluded.password_hash""",
                encoder.encode(rawPassword), Instant.now().toString());
    }

    public boolean matches(String rawPassword) {
        String hash = jdbc.query("SELECT password_hash FROM admin_setup WHERE id = 1",
                (rs, i) -> rs.getString(1)).stream().findFirst().orElse(null);
        if (hash == null) {
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }
}
