package com.leadrat.aisdk.audit;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AuditLogService {

    private final JdbcTemplate jdbc;

    public AuditLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String question, String targetSummary, String entitiesTouched, int totalRows,
                       boolean cached, long latencyMs, String model) {
        jdbc.update("""
                INSERT INTO query_audit_log (requested_at, question, target_summary, entities_touched, total_rows, cached, latency_ms, llm_model)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                Instant.now().toString(), question, targetSummary, entitiesTouched, totalRows, cached ? 1 : 0, latencyMs, model);
    }

    public List<Map<String, Object>> recent(int limit) {
        return jdbc.queryForList("SELECT * FROM query_audit_log ORDER BY id DESC LIMIT ?", limit);
    }
}
