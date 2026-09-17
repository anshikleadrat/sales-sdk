package com.leadrat.aisdk.query;

import java.util.List;
import java.util.Map;

public record TraversalResult(String entity,
                              String id,
                              Map<String, Object> self,
                              List<ParentNode> parents,
                              List<ChildGroup> children) {

    public record ParentNode(int level, String relation, String entity, Map<String, Object> data) {}

    public record ChildGroup(String relation, String entity, long count, List<Map<String, Object>> items) {}
}
