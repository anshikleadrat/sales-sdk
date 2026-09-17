package com.leadrat.aisdk.configure;

import com.leadrat.aisdk.introspection.EntityMetadata;
import com.leadrat.aisdk.introspection.SensitiveFieldHeuristics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConfigRepository {

    private final JdbcTemplate jdbc;

    public ConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<EntityConfig> ENTITY_MAPPER = (rs, i) -> new EntityConfig(
            rs.getString("entity_name"), rs.getString("table_name"), rs.getInt("enabled") == 1,
            rs.getString("description"), rs.getInt("present") == 1);

    private static final RowMapper<FieldConfig> FIELD_MAPPER = (rs, i) -> new FieldConfig(
            rs.getString("entity_name"), rs.getString("field_name"), rs.getString("java_type"),
            rs.getInt("exposed_to_llm") == 1, rs.getInt("sensitive") == 1,
            rs.getString("description"), rs.getInt("present") == 1);

    private static final RowMapper<RelationshipConfig> RELATIONSHIP_MAPPER = (rs, i) -> new RelationshipConfig(
            rs.getString("entity_name"), rs.getString("field_name"), rs.getString("related_entity"),
            rs.getString("relationship_type"), rs.getString("direction"), rs.getInt("traverse_enabled") == 1,
            rs.getString("mapped_by"), rs.getInt("present") == 1);

    private static final RowMapper<GuardrailConfig> GUARDRAIL_MAPPER = (rs, i) -> new GuardrailConfig(
            rs.getString("entity_name"), rs.getInt("max_rows"), rs.getInt("max_child_depth"),
            rs.getInt("max_parent_depth"), rs.getString("custom_prompt_instructions"));

    public synchronized void persistDiscovery(List<EntityMetadata> discovered) {
        String now = Instant.now().toString();
        jdbc.update("UPDATE entity_config SET present = 0");
        jdbc.update("UPDATE field_config SET present = 0");
        jdbc.update("UPDATE relationship_config SET present = 0");

        for (EntityMetadata meta : discovered) {
            jdbc.update("""
                    INSERT INTO entity_config (entity_name, table_name, enabled, discovered_at, updated_at, present)
                    VALUES (?, ?, 0, ?, ?, 1)
                    ON CONFLICT(entity_name) DO UPDATE SET table_name = excluded.table_name, present = 1""",
                    meta.entityName(), meta.tableName(), now, now);

            for (EntityMetadata.DiscoveredField field : meta.fields()) {
                jdbc.update("""
                        INSERT INTO field_config (entity_name, field_name, java_type, exposed_to_llm, sensitive, present)
                        VALUES (?, ?, ?, 1, ?, 1)
                        ON CONFLICT(entity_name, field_name) DO UPDATE SET java_type = excluded.java_type, present = 1""",
                        meta.entityName(), field.fieldName(), field.javaType(),
                        SensitiveFieldHeuristics.isSensitive(field.fieldName()) ? 1 : 0);
            }

            for (EntityMetadata.DiscoveredRelationship rel : meta.relationships()) {
                jdbc.update("""
                        INSERT INTO relationship_config (entity_name, field_name, related_entity, relationship_type, direction, traverse_enabled, mapped_by, present)
                        VALUES (?, ?, ?, ?, ?, 0, ?, 1)
                        ON CONFLICT(entity_name, field_name) DO UPDATE SET related_entity = excluded.related_entity,
                            relationship_type = excluded.relationship_type, direction = excluded.direction,
                            mapped_by = excluded.mapped_by, present = 1""",
                        meta.entityName(), rel.fieldName(), rel.relatedEntity(), rel.relationshipType(),
                        rel.direction(), rel.mappedBy());
            }

            jdbc.update("""
                    INSERT INTO guardrail_config (entity_name, max_rows, max_child_depth, max_parent_depth, updated_at)
                    VALUES (?, 50, 1, 2, ?)
                    ON CONFLICT(entity_name) DO NOTHING""", meta.entityName(), now);
        }
        bumpConfigVersion();
    }

    public List<EntityConfig> entities() {
        return jdbc.query("SELECT * FROM entity_config ORDER BY entity_name", ENTITY_MAPPER);
    }

    public Optional<EntityConfig> entity(String entityName) {
        return jdbc.query("SELECT * FROM entity_config WHERE entity_name = ?", ENTITY_MAPPER, entityName).stream().findFirst();
    }

    public boolean entityEnabled(String entityName) {
        return entity(entityName).map(EntityConfig::enabled).orElse(false);
    }

    public List<FieldConfig> fields() {
        return jdbc.query("SELECT * FROM field_config ORDER BY entity_name, field_name", FIELD_MAPPER);
    }

    public List<FieldConfig> fields(String entityName) {
        return jdbc.query("SELECT * FROM field_config WHERE entity_name = ? ORDER BY field_name", FIELD_MAPPER, entityName);
    }

    public List<RelationshipConfig> relationships() {
        return jdbc.query("SELECT * FROM relationship_config ORDER BY entity_name, field_name", RELATIONSHIP_MAPPER);
    }

    public List<RelationshipConfig> relationships(String entityName) {
        return jdbc.query("SELECT * FROM relationship_config WHERE entity_name = ? ORDER BY field_name", RELATIONSHIP_MAPPER, entityName);
    }

    public Map<String, GuardrailConfig> guardrails() {
        return jdbc.query("SELECT * FROM guardrail_config", GUARDRAIL_MAPPER).stream()
                .collect(Collectors.toMap(GuardrailConfig::entityName, g -> g));
    }

    public GuardrailConfig guardrail(String entityName) {
        return jdbc.query("SELECT * FROM guardrail_config WHERE entity_name = ?", GUARDRAIL_MAPPER, entityName)
                .stream().findFirst().orElseGet(() -> GuardrailConfig.defaults(entityName));
    }

    public synchronized void saveEntitySelection(List<EntityConfig> entities, List<FieldConfig> fields,
                                                 List<RelationshipConfig> relationships) {
        String now = Instant.now().toString();
        for (EntityConfig entity : entities) {
            jdbc.update("UPDATE entity_config SET enabled = ?, description = ?, updated_at = ? WHERE entity_name = ?",
                    entity.enabled() ? 1 : 0, entity.description(), now, entity.entityName());
        }
        for (FieldConfig field : fields) {
            jdbc.update("UPDATE field_config SET exposed_to_llm = ?, sensitive = ?, description = ? WHERE entity_name = ? AND field_name = ?",
                    field.exposedToLlm() ? 1 : 0, field.sensitive() ? 1 : 0, field.description(),
                    field.entityName(), field.fieldName());
        }
        for (RelationshipConfig rel : relationships) {
            jdbc.update("UPDATE relationship_config SET traverse_enabled = ? WHERE entity_name = ? AND field_name = ?",
                    rel.traverseEnabled() ? 1 : 0, rel.entityName(), rel.fieldName());
        }
        bumpConfigVersion();
    }

    public synchronized void saveGuardrails(List<GuardrailConfig> guardrails) {
        String now = Instant.now().toString();
        for (GuardrailConfig g : guardrails) {
            jdbc.update("""
                    INSERT INTO guardrail_config (entity_name, max_rows, max_child_depth, max_parent_depth, custom_prompt_instructions, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(entity_name) DO UPDATE SET max_rows = excluded.max_rows,
                        max_child_depth = excluded.max_child_depth, max_parent_depth = excluded.max_parent_depth,
                        custom_prompt_instructions = excluded.custom_prompt_instructions, updated_at = excluded.updated_at""",
                    g.entityName(), g.maxRows(), g.maxChildDepth(), g.maxParentDepth(), g.customPromptInstructions(), now);
        }
        bumpConfigVersion();
    }

    public long configVersion() {
        Long version = jdbc.queryForObject("SELECT version FROM config_meta WHERE id = 1", Long.class);
        return version == null ? 1L : version;
    }

    private void bumpConfigVersion() {
        jdbc.update("UPDATE config_meta SET version = version + 1 WHERE id = 1");
    }
}
