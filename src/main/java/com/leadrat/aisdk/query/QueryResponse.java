package com.leadrat.aisdk.query;

import java.util.List;
import java.util.Map;

public record QueryResponse(String answer,
                            List<QueryRequest.Target> targets,
                            Map<String, Object> data,
                            Meta meta) {

    public record Meta(boolean cached, String generatedAt, String plannerModel, String summarizerModel,
                       int parentDepth, int childDepth, long latencyMs) {}

    public QueryResponse asCached(long latencyMs) {
        return new QueryResponse(answer, targets, data,
                new Meta(true, meta.generatedAt(), meta.plannerModel(), meta.summarizerModel(),
                        meta.parentDepth(), meta.childDepth(), latencyMs));
    }
}
