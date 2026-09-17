package com.leadrat.aisdk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public class SqliteConfig {

    private static final Logger log = LoggerFactory.getLogger(SqliteConfig.class);

    public static DataSource dataSource(AiSdkProperties properties) {
        Path path = resolve(properties);
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + path);
        return ds;
    }

    public static void initSchema(JdbcTemplate jdbc) {
        jdbc.execute("PRAGMA journal_mode=WAL");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS admin_setup (
                id                 INTEGER PRIMARY KEY CHECK (id = 1),
                password_hash      TEXT NOT NULL,
                setup_completed_at TEXT NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS entity_config (
                entity_name   TEXT PRIMARY KEY,
                table_name    TEXT NOT NULL,
                enabled       INTEGER NOT NULL DEFAULT 0,
                description   TEXT,
                discovered_at TEXT NOT NULL,
                updated_at    TEXT,
                present       INTEGER NOT NULL DEFAULT 1
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS field_config (
                entity_name    TEXT NOT NULL,
                field_name     TEXT NOT NULL,
                java_type      TEXT,
                exposed_to_llm INTEGER NOT NULL DEFAULT 1,
                sensitive      INTEGER NOT NULL DEFAULT 0,
                description    TEXT,
                present        INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (entity_name, field_name)
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS relationship_config (
                entity_name       TEXT NOT NULL,
                field_name        TEXT NOT NULL,
                related_entity    TEXT NOT NULL,
                relationship_type TEXT NOT NULL,
                direction         TEXT NOT NULL,
                traverse_enabled  INTEGER NOT NULL DEFAULT 0,
                mapped_by         TEXT,
                present           INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (entity_name, field_name)
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS guardrail_config (
                entity_name                TEXT PRIMARY KEY,
                max_rows                   INTEGER NOT NULL DEFAULT 50,
                max_child_depth            INTEGER NOT NULL DEFAULT 1,
                max_parent_depth           INTEGER NOT NULL DEFAULT 2,
                custom_prompt_instructions TEXT,
                updated_at                 TEXT
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS query_audit_log (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                requested_at    TEXT NOT NULL,
                question        TEXT,
                target_summary  TEXT,
                entities_touched TEXT,
                total_rows      INTEGER,
                cached          INTEGER,
                latency_ms      INTEGER,
                llm_model       TEXT
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sdk_setting (
                name       TEXT PRIMARY KEY,
                value      TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sdk_secret (
                name       TEXT PRIMARY KEY,
                value      TEXT NOT NULL,
                created_at TEXT NOT NULL
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS meeting (
                id                       TEXT PRIMARY KEY,
                lead_entity              TEXT,
                lead_id                  TEXT NOT NULL,
                title                    TEXT,
                agenda                   TEXT,
                scheduled_at             TEXT NOT NULL,
                duration_minutes         INTEGER NOT NULL DEFAULT 60,
                meeting_link             TEXT,
                conference_id            TEXT,
                calendar_event_id        TEXT,
                calendar_ical_uid        TEXT,
                calendar_sync_status     TEXT,
                calendar_sync_error      TEXT,
                calendar_synced_at       TEXT,
                recall_calendar_event_id TEXT,
                recall_bot_id            TEXT,
                recall_bot_status        TEXT,
                recall_scheduled_at      TEXT,
                recall_error             TEXT,
                status                   TEXT NOT NULL DEFAULT 'SCHEDULED',
                created_at               TEXT NOT NULL,
                updated_at               TEXT
            )""");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_meeting_lead ON meeting (lead_id, scheduled_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_meeting_bot ON meeting (recall_bot_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_meeting_ical ON meeting (calendar_ical_uid)");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS meeting_discussion (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                meeting_id    TEXT,
                lead_entity   TEXT,
                lead_id       TEXT,
                provider      TEXT NOT NULL DEFAULT 'RECALL_AI',
                external_id   TEXT NOT NULL,
                meeting_title TEXT,
                meeting_url   TEXT,
                discussion    TEXT,
                participants  TEXT,
                started_at    TEXT,
                ended_at      TEXT,
                occurred_at   TEXT NOT NULL,
                received_at   TEXT NOT NULL,
                match_status  TEXT NOT NULL DEFAULT 'UNMATCHED',
                UNIQUE (provider, external_id)
            )""");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_discussion_lead ON meeting_discussion (lead_id, occurred_at DESC)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_discussion_meeting ON meeting_discussion (meeting_id)");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS google_credential (
                id                      INTEGER PRIMARY KEY CHECK (id = 1),
                google_email            TEXT,
                refresh_token_encrypted TEXT NOT NULL,
                scopes                  TEXT,
                recall_calendar_id      TEXT,
                recall_status           TEXT,
                recall_error            TEXT,
                recall_synced_at        TEXT,
                connected_at            TEXT,
                last_refresh_at         TEXT,
                revoked_at              TEXT
            )""");
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS config_meta (
                id      INTEGER PRIMARY KEY CHECK (id = 1),
                version INTEGER NOT NULL
            )""");
        jdbc.update("INSERT OR IGNORE INTO config_meta (id, version) VALUES (1, 1)");
    }

    private static Path resolve(AiSdkProperties properties) {
        Path configured = Paths.get(properties.getStorage().getSqlitePath()).toAbsolutePath();
        try {
            prepareFile(configured);
            return configured;
        } catch (RuntimeException e) {
            Path fallback = Paths.get(System.getProperty("java.io.tmpdir"), "ai-sdk-data", "sdk-config.db")
                    .toAbsolutePath();
            if (fallback.equals(configured)) {
                throw e;
            }
            prepareFile(fallback);
            log.warn("ai-sdk: {} is not writable, using {} instead — set ai-sdk.storage.sqlite-path to a durable "
                    + "location so configuration survives restarts", configured, fallback);
            properties.getStorage().setSqlitePath(fallback.toString());
            return fallback;
        }
    }

    private static void prepareFile(Path path) {
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            try {
                Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                log.debug("POSIX permissions unsupported for {}", path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare SDK SQLite store at " + path, e);
        }
    }
}
