package com.leadrat.aisdk.config;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

public class SqliteStore {

    private final JdbcTemplate jdbcTemplate;

    public SqliteStore(AiSdkProperties properties) {
        DataSource dataSource = SqliteConfig.dataSource(properties);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        SqliteConfig.initSchema(this.jdbcTemplate);
    }

    public JdbcTemplate jdbc() {
        return jdbcTemplate;
    }
}
