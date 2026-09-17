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
        Path path = Paths.get(properties.getStorage().getSqlitePath()).toAbsolutePath();
        prepareFile(path);
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
            CREATE TABLE IF NOT EXISTS config_meta (
                id      INTEGER PRIMARY KEY CHECK (id = 1),
                version INTEGER NOT NULL
            )""");
        jdbc.update("INSERT OR IGNORE INTO config_meta (id, version) VALUES (1, 1)");
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
