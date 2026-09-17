package com.leadrat.aisdk.query;

import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.configure.GuardrailConfig;
import com.leadrat.aisdk.configure.RelationshipConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class QueryPlanValidator {

    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "GT", "GTE", "LT", "LTE", "LIKE", "IS_NULL", "IS_NOT_NULL");

    private final AiSdkProperties properties;
    private final SchemaCatalog catalog;

    public QueryPlanValidator(AiSdkProperties properties, SchemaCatalog catalog) {
        this.properties = properties;
        this.catalog = catalog;
    }

    public EffectivePlan validate(QueryPlan plan, QueryRequest request, List<String> targetEntities) {
        AiSdkProperties.Query limits = properties.getQuery();

        int entityParentCeiling = limits.getMaxParentDepth();
        int entityChildCeiling = limits.getMaxChildDepth();
        int rowCeiling = limits.getMaxChildrenPerRelation();
        for (String entity : targetEntities) {
            GuardrailConfig guardrail = catalog.guardrail(entity);
            entityParentCeiling = Math.min(entityParentCeiling, guardrail.maxParentDepth());
            entityChildCeiling = Math.min(entityChildCeiling, guardrail.maxChildDepth());
            rowCeiling = Math.min(rowCeiling, guardrail.maxRows());
        }

        int parentDepth = firstNonNull(
                request.options() == null ? null : request.options().parentDepth(),
                plan == null ? null : plan.parentDepth(),
                limits.getDefaultParentDepth());
        int childDepth = firstNonNull(
                request.options() == null ? null : request.options().childDepth(),
                plan == null ? null : plan.childDepth(),
                limits.getDefaultChildDepth());
        int maxChildren = firstNonNull(
                request.options() == null ? null : request.options().maxChildrenPerRelation(),
                null,
                limits.getMaxChildrenPerRelation());

        parentDepth = clamp(parentDepth, 0, entityParentCeiling);
        childDepth = clamp(childDepth, 0, entityChildCeiling);
        maxChildren = clamp(maxChildren, 1, rowCeiling);

        Map<String, List<QueryPlan.Filter>> filters = new LinkedHashMap<>();
        List<String> allowed = new ArrayList<>();
        if (plan != null && plan.relations() != null) {
            for (QueryPlan.RelationSelection selection : plan.relations()) {
                if (selection == null || selection.entity() == null || selection.relation() == null) {
                    continue;
                }
                if (!catalog.enabled(selection.entity())) {
                    continue;
                }
                RelationshipConfig relation = resolve(selection.entity(), selection.relation());
                if (relation == null) {
                    continue;
                }
                String key = selection.entity() + "." + selection.relation();
                allowed.add(key);
                filters.put(key, sanitizeFilters(relation.relatedEntity(), selection.filters()));
            }
        }

        return new EffectivePlan(parentDepth, childDepth, maxChildren, filters, allowed, !allowed.isEmpty());
    }

    private RelationshipConfig resolve(String entity, String relation) {
        List<RelationshipConfig> candidates = new ArrayList<>(catalog.traversableParents(entity));
        candidates.addAll(catalog.traversableChildren(entity));
        return candidates.stream().filter(r -> r.fieldName().equals(relation)).findFirst().orElse(null);
    }

    private List<QueryPlan.Filter> sanitizeFilters(String relatedEntity, List<QueryPlan.Filter> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        Set<String> exposed = catalog.exposedFields(relatedEntity);
        return filters.stream()
                .filter(f -> f != null && f.field() != null && f.operator() != null)
                .filter(f -> exposed.contains(f.field()))
                .filter(f -> OPERATORS.contains(f.operator().toUpperCase()))
                .map(f -> new QueryPlan.Filter(f.field(), f.operator().toUpperCase(), f.value()))
                .collect(Collectors.toList());
    }

    private int firstNonNull(Integer a, Integer b, int fallback) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        return fallback;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
