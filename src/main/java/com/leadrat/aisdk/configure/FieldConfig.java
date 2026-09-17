package com.leadrat.aisdk.configure;

public record FieldConfig(String entityName, String fieldName, String javaType, boolean exposedToLlm,
                          boolean sensitive, String description, boolean present) {}
