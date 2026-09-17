package com.leadrat.aisdk.configure;

public record GuardrailConfig(String entityName, int maxRows, int maxChildDepth, int maxParentDepth,
                              String customPromptInstructions) {

    public static GuardrailConfig defaults(String entityName) {
        return new GuardrailConfig(entityName, 50, 1, 2, null);
    }
}
