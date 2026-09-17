package com.leadrat.aisdk.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryPlan(Integer parentDepth, Integer childDepth, List<RelationSelection> relations, String focus) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelationSelection(String entity, String relation, List<Filter> filters) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Filter(String field, String operator, String value) {}
}
