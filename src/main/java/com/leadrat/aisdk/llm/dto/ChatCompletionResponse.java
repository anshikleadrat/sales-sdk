package com.leadrat.aisdk.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(String id, String model, List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(int index, ChatMessage message, String finish_reason) {}

    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).message() == null) {
            return "";
        }
        return choices.get(0).message().content() == null ? "" : choices.get(0).message().content();
    }
}
