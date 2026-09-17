package com.leadrat.aisdk.query;

import com.leadrat.aisdk.introspection.EntityMetadata;
import com.leadrat.aisdk.introspection.RelationshipClassifier;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ResultSerializer {

    private static final int MAX_STRING_LENGTH = 2000;

    private final SchemaCatalog catalog;

    public ResultSerializer(SchemaCatalog catalog) {
        this.catalog = catalog;
    }

    public Map<String, Object> serialize(Object entity, EntityMetadata metadata) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (entity == null || metadata == null) {
            return out;
        }
        Object unproxied = Hibernate.unproxy(entity);
        Set<String> exposed = catalog.exposedFields(metadata.entityName());

        if (metadata.idFieldName() != null) {
            out.put(metadata.idFieldName(), normalize(read(unproxied, metadata.idFieldName())));
        }
        for (EntityMetadata.DiscoveredField field : metadata.fields()) {
            if (field.fieldName().equals(metadata.idFieldName()) || !exposed.contains(field.fieldName())) {
                continue;
            }
            out.put(field.fieldName(), normalize(read(unproxied, field.fieldName())));
        }
        return out;
    }

    public Object readId(Object entity, EntityMetadata metadata) {
        if (entity == null || metadata == null || metadata.idFieldName() == null) {
            return null;
        }
        return read(Hibernate.unproxy(entity), metadata.idFieldName());
    }

    public Object read(Object target, String fieldName) {
        Field field = RelationshipClassifier.findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String string) {
            return truncate(string);
        }
        if (value instanceof Temporal || value instanceof Date || value instanceof UUID
                || value instanceof Enum<?> || value instanceof BigDecimal || value instanceof BigInteger) {
            return value.toString();
        }
        return truncate(String.valueOf(value));
    }

    private String truncate(String value) {
        return value.length() <= MAX_STRING_LENGTH ? value : value.substring(0, MAX_STRING_LENGTH) + "...";
    }
}
