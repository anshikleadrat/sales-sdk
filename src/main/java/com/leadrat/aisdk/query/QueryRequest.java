package com.leadrat.aisdk.query;

import java.util.List;

public record QueryRequest(String question, List<Target> targets, Options options) {

    public record Target(String entity, String id, String phone) {}

    public record Options(Integer childDepth, Integer parentDepth, Integer maxChildrenPerRelation) {}
}
