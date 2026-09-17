package com.leadrat.aisdk.configure;

public record RelationshipConfig(String entityName, String fieldName, String relatedEntity, String relationshipType,
                                 String direction, boolean traverseEnabled, String mappedBy, boolean present) {

    public boolean parent() {
        return "PARENT".equals(direction);
    }

    public boolean child() {
        return "CHILD".equals(direction);
    }
}
