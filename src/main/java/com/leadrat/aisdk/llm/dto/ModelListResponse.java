package com.leadrat.aisdk.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Comparator;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelListResponse(List<Model> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(String id, String name) {}

    public List<String> ids() {
        if (data == null) {
            return List.of();
        }
        return data.stream()
                .map(Model::id)
                .filter(id -> id != null && !id.isBlank())
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
