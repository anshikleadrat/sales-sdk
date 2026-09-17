package com.leadrat.aisdk.introspection;

import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.metamodel.Attribute;

import java.lang.reflect.Field;

public class RelationshipClassifier {

    public static String type(Attribute.PersistentAttributeType type) {
        return switch (type) {
            case MANY_TO_ONE -> "MANY_TO_ONE";
            case ONE_TO_MANY -> "ONE_TO_MANY";
            case ONE_TO_ONE -> "ONE_TO_ONE";
            case MANY_TO_MANY -> "MANY_TO_MANY";
            default -> "UNKNOWN";
        };
    }

    public static String direction(String relationshipType) {
        return switch (relationshipType) {
            case "MANY_TO_ONE", "ONE_TO_ONE" -> "PARENT";
            case "ONE_TO_MANY", "MANY_TO_MANY" -> "CHILD";
            default -> "CHILD";
        };
    }

    public static String mappedBy(Class<?> owner, String fieldName) {
        Field field = findField(owner, fieldName);
        if (field == null) {
            return null;
        }
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        if (oneToMany != null && !oneToMany.mappedBy().isBlank()) {
            return oneToMany.mappedBy();
        }
        ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
        if (manyToMany != null && !manyToMany.mappedBy().isBlank()) {
            return manyToMany.mappedBy();
        }
        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        if (oneToOne != null && !oneToOne.mappedBy().isBlank()) {
            return oneToOne.mappedBy();
        }
        return null;
    }

    public static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
