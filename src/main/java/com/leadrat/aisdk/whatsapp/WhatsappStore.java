package com.leadrat.aisdk.whatsapp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class WhatsappStore {

    private final JdbcTemplate jdbc;

    public WhatsappStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<WhatsappMessage> mapper = (rs, rowNum) -> new WhatsappMessage(
            rs.getLong("id"),
            rs.getString("phone"),
            rs.getString("lead_entity"),
            rs.getString("lead_id"),
            rs.getString("external_id"),
            rs.getString("direction"),
            (Integer) rs.getObject("status"),
            rs.getString("sender_name"),
            rs.getString("body"),
            rs.getString("media_type"),
            rs.getString("template_name"),
            instant(rs, "sent_at"),
            instant(rs, "fetched_at"));

    public void upsert(WhatsappMessage message) {
        jdbc.update("""
                INSERT INTO whatsapp_message (phone, lead_entity, lead_id, external_id, direction, status,
                    sender_name, body, media_type, template_name, sent_at, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(phone, external_id) DO UPDATE SET
                    lead_entity = excluded.lead_entity,
                    lead_id = excluded.lead_id,
                    direction = excluded.direction,
                    status = excluded.status,
                    sender_name = excluded.sender_name,
                    body = excluded.body,
                    media_type = excluded.media_type,
                    template_name = excluded.template_name,
                    fetched_at = excluded.fetched_at""",
                message.phone(), message.leadEntity(), message.leadId(), message.externalId(),
                message.direction(), message.status(), message.senderName(), message.body(),
                message.mediaType(), message.templateName(), text(message.sentAt()), text(message.fetchedAt()));
    }

    public List<WhatsappMessage> forPhone(String phone, int limit) {
        return jdbc.query("""
                SELECT * FROM whatsapp_message
                WHERE phone = ? AND body IS NOT NULL
                ORDER BY sent_at DESC, id DESC LIMIT ?""", mapper, phone, limit);
    }

    public String fingerprint(List<String> phones) {
        if (phones == null || phones.isEmpty()) {
            return "";
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(phones.size(), "?"));
        String fingerprint = jdbc.queryForObject("""
                SELECT COUNT(*) || ':' || COALESCE(MAX(fetched_at), '')
                FROM whatsapp_message WHERE phone IN (%s)""".formatted(placeholders),
                String.class, phones.toArray());
        return fingerprint == null ? "" : fingerprint;
    }

    public Optional<Instant> lastFetchedAt(String phone) {
        return jdbc.query("SELECT last_fetched_at FROM whatsapp_sync WHERE phone = ?",
                (rs, rowNum) -> instant(rs, "last_fetched_at"), phone).stream().findFirst();
    }

    public void recordSync(String phone, Instant lastMessageAt, int messageCount, String error) {
        jdbc.update("""
                INSERT INTO whatsapp_sync (phone, last_fetched_at, last_message_at, message_count, sync_error)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(phone) DO UPDATE SET
                    last_fetched_at = excluded.last_fetched_at,
                    last_message_at = excluded.last_message_at,
                    message_count = excluded.message_count,
                    sync_error = excluded.sync_error""",
                phone, text(Instant.now()), text(lastMessageAt), messageCount, error);
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
