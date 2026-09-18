package com.leadrat.aisdk.query;

import com.leadrat.aisdk.configure.ConfigRepository;
import com.leadrat.aisdk.configure.FieldConfig;
import com.leadrat.aisdk.configure.GuardrailConfig;
import com.leadrat.aisdk.configure.RelationshipConfig;
import com.leadrat.aisdk.introspection.EntityMetadata;
import com.leadrat.aisdk.introspection.SchemaIntrospector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SchemaCatalog {

    private final ConfigRepository configRepository;
    private final SchemaIntrospector introspector;

    public SchemaCatalog(ConfigRepository configRepository, SchemaIntrospector introspector) {
        this.configRepository = configRepository;
        this.introspector = introspector;
    }

    public boolean enabled(String entityName) {
        return configRepository.entityEnabled(entityName);
    }

    public EntityMetadata metadata(String entityName) {
        return introspector.metadataFor(entityName);
    }

    public GuardrailConfig guardrail(String entityName) {
        return configRepository.guardrail(entityName);
    }

    public Set<String> exposedFields(String entityName) {
        return configRepository.fields(entityName).stream()
                .filter(FieldConfig::present)
                .filter(FieldConfig::exposedToLlm)
                .filter(f -> !f.sensitive())
                .map(FieldConfig::fieldName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<RelationshipConfig> traversableParents(String entityName) {
        return configRepository.relationships(entityName).stream()
                .filter(RelationshipConfig::present)
                .filter(RelationshipConfig::traverseEnabled)
                .filter(RelationshipConfig::parent)
                .filter(r -> enabled(r.relatedEntity()))
                .toList();
    }

    public List<RelationshipConfig> traversableChildren(String entityName) {
        return configRepository.relationships(entityName).stream()
                .filter(RelationshipConfig::present)
                .filter(RelationshipConfig::traverseEnabled)
                .filter(RelationshipConfig::child)
                .filter(r -> enabled(r.relatedEntity()))
                .toList();
    }

    public String describe(List<String> rootEntities) {
        StringBuilder sb = new StringBuilder();
        Set<String> visited = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>(rootEntities);
        while (!queue.isEmpty()) {
            String entityName = queue.remove(0);
            if (!visited.add(entityName) || !enabled(entityName)) {
                continue;
            }
            sb.append("ENTITY ").append(entityName).append('\n');
            configRepository.entity(entityName)
                    .filter(e -> e.description() != null && !e.description().isBlank())
                    .ifPresent(e -> sb.append("  description: ").append(e.description()).append('\n'));
            sb.append("  fields:\n");
            for (FieldConfig field : configRepository.fields(entityName)) {
                if (!field.present() || !field.exposedToLlm() || field.sensitive()) {
                    continue;
                }
                sb.append("    - ").append(field.fieldName()).append(" (").append(field.javaType()).append(')');
                if (field.description() != null && !field.description().isBlank()) {
                    sb.append(" : ").append(field.description());
                }
                sb.append('\n');
            }
            List<RelationshipConfig> parents = traversableParents(entityName);
            List<RelationshipConfig> children = traversableChildren(entityName);
            if (!parents.isEmpty()) {
                sb.append("  parent relations:\n");
                for (RelationshipConfig rel : parents) {
                    sb.append("    - ").append(rel.fieldName()).append(" -> ").append(rel.relatedEntity())
                            .append(" (").append(rel.relationshipType()).append(")\n");
                    queue.add(rel.relatedEntity());
                }
            }
            if (!children.isEmpty()) {
                sb.append("  child relations:\n");
                for (RelationshipConfig rel : children) {
                    sb.append("    - ").append(rel.fieldName()).append(" -> ").append(rel.relatedEntity())
                            .append(" (").append(rel.relationshipType()).append(")\n");
                    queue.add(rel.relatedEntity());
                }
            }
            GuardrailConfig guardrail = guardrail(entityName);
            if (guardrail.customPromptInstructions() != null && !guardrail.customPromptInstructions().isBlank()) {
                sb.append("  instructions: ").append(guardrail.customPromptInstructions()).append('\n');
            }
        }
        return sb.toString();
    }

    public List<String> instructionsFor(List<String> entityNames) {
        List<String> out = new ArrayList<>();
        for (String entityName : new LinkedHashSet<>(entityNames)) {
            GuardrailConfig guardrail = guardrail(entityName);
            if (guardrail.customPromptInstructions() != null && !guardrail.customPromptInstructions().isBlank()) {
                out.add(entityName + ": " + guardrail.customPromptInstructions());
            }
        }
        return out;
    }
}
