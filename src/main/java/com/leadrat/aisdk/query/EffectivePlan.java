package com.leadrat.aisdk.query;

import java.util.List;
import java.util.Map;

public record EffectivePlan(int parentDepth,
                            int childDepth,
                            int maxChildrenPerRelation,
                            Map<String, List<QueryPlan.Filter>> relationFilters,
                            List<String> allowedRelations,
                            boolean relationAllowListActive) {

    public boolean allows(String entity, String relation) {
        return !relationAllowListActive || allowedRelations.contains(entity + "." + relation);
    }

    public List<QueryPlan.Filter> filtersFor(String entity, String relation) {
        return relationFilters.getOrDefault(entity + "." + relation, List.of());
    }
}
