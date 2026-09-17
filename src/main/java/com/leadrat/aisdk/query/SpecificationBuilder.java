package com.leadrat.aisdk.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SpecificationBuilder {

    public List<Predicate> build(CriteriaBuilder cb, Root<?> root, List<QueryPlan.Filter> filters) {
        List<Predicate> predicates = new ArrayList<>();
        if (filters == null) {
            return predicates;
        }
        for (QueryPlan.Filter filter : filters) {
            Predicate predicate = toPredicate(cb, root, filter);
            if (predicate != null) {
                predicates.add(predicate);
            }
        }
        return predicates;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate toPredicate(CriteriaBuilder cb, Root<?> root, QueryPlan.Filter filter) {
        Path<Object> path;
        try {
            path = root.get(filter.field());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String operator = filter.operator();
        if ("IS_NULL".equals(operator)) {
            return cb.isNull(path);
        }
        if ("IS_NOT_NULL".equals(operator)) {
            return cb.isNotNull(path);
        }
        if (filter.value() == null) {
            return null;
        }
        Object value = coerce(filter.value(), path.getJavaType());
        if (value == null) {
            return null;
        }
        return switch (operator) {
            case "EQ" -> cb.equal(path, value);
            case "NE" -> cb.notEqual(path, value);
            case "LIKE" -> path.getJavaType() == String.class
                    ? cb.like(cb.lower(path.as(String.class)), "%" + filter.value().toLowerCase() + "%")
                    : null;
            case "GT" -> value instanceof Comparable comparable ? cb.greaterThan(path.as(Comparable.class), comparable) : null;
            case "GTE" -> value instanceof Comparable comparable ? cb.greaterThanOrEqualTo(path.as(Comparable.class), comparable) : null;
            case "LT" -> value instanceof Comparable comparable ? cb.lessThan(path.as(Comparable.class), comparable) : null;
            case "LTE" -> value instanceof Comparable comparable ? cb.lessThanOrEqualTo(path.as(Comparable.class), comparable) : null;
            default -> null;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object coerce(String raw, Class<?> targetType) {
        try {
            if (targetType == String.class) {
                return raw;
            }
            if (targetType == Long.class || targetType == long.class) {
                return Long.valueOf(raw);
            }
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.valueOf(raw);
            }
            if (targetType == Short.class || targetType == short.class) {
                return Short.valueOf(raw);
            }
            if (targetType == Double.class || targetType == double.class) {
                return Double.valueOf(raw);
            }
            if (targetType == Float.class || targetType == float.class) {
                return Float.valueOf(raw);
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(raw);
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.valueOf(raw);
            }
            if (targetType == UUID.class) {
                return UUID.fromString(raw);
            }
            if (targetType == LocalDate.class) {
                return LocalDate.parse(raw);
            }
            if (targetType == LocalDateTime.class) {
                return LocalDateTime.parse(raw);
            }
            if (targetType == OffsetDateTime.class) {
                return OffsetDateTime.parse(raw);
            }
            if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, raw);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }
}
