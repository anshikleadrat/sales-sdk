package com.leadrat.aisdk.introspection;

import java.util.List;

public record EntityMetadata(
        String entityName,
        String tableName,
        Class<?> javaType,
        String idFieldName,
        Class<?> idJavaType,
        List<DiscoveredField> fields,
        List<DiscoveredRelationship> relationships) {

    public record DiscoveredField(String fieldName, String javaType) {}

    public record DiscoveredRelationship(String fieldName,
                                         String relatedEntity,
                                         Class<?> relatedJavaType,
                                         String relationshipType,
                                         String direction,
                                         String mappedBy) {}
}
