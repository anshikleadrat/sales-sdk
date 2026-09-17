package com.leadrat.aisdk.meeting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class GoogleCredentialStore {

    private final JdbcTemplate jdbc;

    public GoogleCredentialStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<GoogleCredential> mapper = (rs, rowNum) -> {
        GoogleCredential credential = new GoogleCredential();
        credential.setGoogleEmail(rs.getString("google_email"));
        credential.setRefreshTokenEncrypted(rs.getString("refresh_token_encrypted"));
        credential.setScopes(rs.getString("scopes"));
        credential.setRecallCalendarId(rs.getString("recall_calendar_id"));
        credential.setRecallStatus(rs.getString("recall_status"));
        credential.setRecallError(rs.getString("recall_error"));
        credential.setRecallSyncedAt(instant(rs, "recall_synced_at"));
        credential.setConnectedAt(instant(rs, "connected_at"));
        credential.setLastRefreshAt(instant(rs, "last_refresh_at"));
        credential.setRevokedAt(instant(rs, "revoked_at"));
        return credential;
    };

    public Optional<GoogleCredential> find() {
        return jdbc.query("SELECT * FROM google_credential WHERE id = 1", mapper).stream().findFirst();
    }

    public Optional<GoogleCredential> findConnected() {
        return find().filter(credential -> credential.getRevokedAt() == null);
    }

    public Optional<GoogleCredential> findByRecallCalendarId(String recallCalendarId) {
        return findConnected().filter(credential -> recallCalendarId != null
                && recallCalendarId.equals(credential.getRecallCalendarId()));
    }

    public void save(GoogleCredential credential) {
        jdbc.update("""
                INSERT INTO google_credential (id, google_email, refresh_token_encrypted, scopes,
                    recall_calendar_id, recall_status, recall_error, recall_synced_at, connected_at,
                    last_refresh_at, revoked_at)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    google_email = excluded.google_email,
                    refresh_token_encrypted = excluded.refresh_token_encrypted,
                    scopes = excluded.scopes,
                    recall_calendar_id = excluded.recall_calendar_id,
                    recall_status = excluded.recall_status,
                    recall_error = excluded.recall_error,
                    recall_synced_at = excluded.recall_synced_at,
                    connected_at = excluded.connected_at,
                    last_refresh_at = excluded.last_refresh_at,
                    revoked_at = excluded.revoked_at""",
                credential.getGoogleEmail(), credential.getRefreshTokenEncrypted(), credential.getScopes(),
                credential.getRecallCalendarId(), credential.getRecallStatus(), credential.getRecallError(),
                text(credential.getRecallSyncedAt()), text(credential.getConnectedAt()),
                text(credential.getLastRefreshAt()), text(credential.getRevokedAt()));
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
