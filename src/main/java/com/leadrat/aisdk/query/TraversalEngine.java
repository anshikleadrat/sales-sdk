package com.leadrat.aisdk.query;

import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.config.ReadOnlyEntityManagerProvider;
import com.leadrat.aisdk.configure.RelationshipConfig;
import com.leadrat.aisdk.introspection.EntityMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TraversalEngine {

    private final ReadOnlyEntityManagerProvider emProvider;
    private final SchemaCatalog catalog;
    private final ResultSerializer serializer;
    private final SpecificationBuilder specificationBuilder;
    private final AiSdkProperties properties;

    public TraversalEngine(ReadOnlyEntityManagerProvider emProvider, SchemaCatalog catalog,
                           ResultSerializer serializer, SpecificationBuilder specificationBuilder,
                           AiSdkProperties properties) {
        this.emProvider = emProvider;
        this.catalog = catalog;
        this.serializer = serializer;
        this.specificationBuilder = specificationBuilder;
        this.properties = properties;
    }

    public List<TraversalResult> traverse(List<QueryRequest.Target> targets, EffectivePlan plan) {
        EntityManager em = emProvider.createEntityManager();
        try {
            List<TraversalResult> results = new ArrayList<>();
            for (QueryRequest.Target target : targets) {
                results.add(traverseOne(em, target, plan));
            }
            return results;
        } finally {
            em.close();
        }
    }

    private TraversalResult traverseOne(EntityManager em, QueryRequest.Target target, EffectivePlan plan) {
        EntityMetadata metadata = requireEnabled(target.entity());
        Object id = SpecificationBuilder.coerce(target.id(), metadata.idJavaType());
        if (id == null) {
            throw new IllegalArgumentException("Unsupported id value '" + target.id() + "' for entity " + target.entity());
        }
        Object root = em.find(metadata.javaType(), id);
        if (root == null) {
            return new TraversalResult(metadata.entityName(), target.id(), Map.of(), List.of(), List.of());
        }
        Set<String> visited = new LinkedHashSet<>();
        visited.add(key(metadata.entityName(), id));
        List<TraversalResult.ParentNode> parents = walkParents(em, root, metadata, plan, plan.parentDepth(), 1, "", visited);
        List<TraversalResult.ChildGroup> children = walkChildren(em, root, metadata, plan, plan.childDepth());
        return new TraversalResult(metadata.entityName(), String.valueOf(id),
                serializer.serialize(root, metadata), parents, children);
    }

    private List<TraversalResult.ParentNode> walkParents(EntityManager em, Object entity, EntityMetadata metadata,
                                                         EffectivePlan plan, int remaining, int level,
                                                         String pathPrefix, Set<String> visited) {
        List<TraversalResult.ParentNode> out = new ArrayList<>();
        if (remaining <= 0) {
            return out;
        }
        for (RelationshipConfig rel : catalog.traversableParents(metadata.entityName())) {
            if (!plan.allows(metadata.entityName(), rel.fieldName())) {
                continue;
            }
            Object parent = serializer.read(Hibernate.unproxy(entity), rel.fieldName());
            if (parent == null) {
                continue;
            }
            parent = Hibernate.unproxy(parent);
            EntityMetadata parentMetadata = catalog.metadata(rel.relatedEntity());
            if (parentMetadata == null || !catalog.enabled(rel.relatedEntity())) {
                continue;
            }
            Object parentId = serializer.readId(parent, parentMetadata);
            if (!visited.add(key(parentMetadata.entityName(), parentId))) {
                continue;
            }
            String relationPath = pathPrefix.isEmpty() ? rel.fieldName() : pathPrefix + "." + rel.fieldName();
            out.add(new TraversalResult.ParentNode(level, relationPath, parentMetadata.entityName(),
                    serializer.serialize(parent, parentMetadata)));
            out.addAll(walkParents(em, parent, parentMetadata, plan, remaining - 1, level + 1, relationPath, visited));
        }
        return out;
    }

    private List<TraversalResult.ChildGroup> walkChildren(EntityManager em, Object entity, EntityMetadata metadata,
                                                          EffectivePlan plan, int remainingDepth) {
        List<TraversalResult.ChildGroup> out = new ArrayList<>();
        if (remainingDepth <= 0) {
            return out;
        }
        for (RelationshipConfig rel : catalog.traversableChildren(metadata.entityName())) {
            if (!plan.allows(metadata.entityName(), rel.fieldName())) {
                continue;
            }
            EntityMetadata childMetadata = catalog.metadata(rel.relatedEntity());
            if (childMetadata == null) {
                continue;
            }
            List<QueryPlan.Filter> filters = plan.filtersFor(metadata.entityName(), rel.fieldName());
            ChildFetch fetch = rel.mappedBy() != null
                    ? fetchByOwner(em, entity, childMetadata, rel.mappedBy(), filters, plan.maxChildrenPerRelation())
                    : fetchByCollection(entity, rel.fieldName(), plan.maxChildrenPerRelation());

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object child : fetch.items()) {
                Object unproxied = Hibernate.unproxy(child);
                Map<String, Object> serialized = new LinkedHashMap<>(serializer.serialize(unproxied, childMetadata));
                List<TraversalResult.ChildGroup> nested =
                        walkChildren(em, unproxied, childMetadata, plan, remainingDepth - 1);
                if (!nested.isEmpty()) {
                    serialized.put("children", nested);
                }
                items.add(serialized);
            }
            out.add(new TraversalResult.ChildGroup(rel.fieldName(), childMetadata.entityName(), fetch.count(), items));
        }
        return out;
    }

    private record ChildFetch(long count, List<?> items) {}

    @SuppressWarnings("unchecked")
    private ChildFetch fetchByOwner(EntityManager em, Object owner, EntityMetadata childMetadata, String mappedBy,
                                    List<QueryPlan.Filter> filters, int maxRows) {
        Class<Object> childType = (Class<Object>) childMetadata.javaType();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object> criteria = cb.createQuery(childType);
        Root<Object> root = criteria.from(childType);
        List<Predicate> predicates = new ArrayList<>();
        try {
            predicates.add(cb.equal(root.get(mappedBy), owner));
        } catch (IllegalArgumentException e) {
            return new ChildFetch(0, List.of());
        }
        predicates.addAll(specificationBuilder.build(cb, root, filters));
        criteria.select(root).where(cb.and(predicates.toArray(new Predicate[0])));

        TypedQuery<Object> query = em.createQuery(criteria);
        query.setMaxResults(maxRows);
        query.setHint("jakarta.persistence.query.timeout", properties.getQuery().getDbTimeoutSeconds() * 1000);
        List<Object> items = query.getResultList();

        CriteriaQuery<Long> countCriteria = cb.createQuery(Long.class);
        Root<Object> countRoot = countCriteria.from(childType);
        List<Predicate> countPredicates = new ArrayList<>();
        countPredicates.add(cb.equal(countRoot.get(mappedBy), owner));
        countPredicates.addAll(specificationBuilder.build(cb, countRoot, filters));
        countCriteria.select(cb.count(countRoot)).where(cb.and(countPredicates.toArray(new Predicate[0])));
        TypedQuery<Long> countQuery = em.createQuery(countCriteria);
        countQuery.setHint("jakarta.persistence.query.timeout", properties.getQuery().getDbTimeoutSeconds() * 1000);
        Long count = countQuery.getSingleResult();
        return new ChildFetch(count == null ? items.size() : count, items);
    }

    private ChildFetch fetchByCollection(Object owner, String fieldName, int maxRows) {
        Object value = serializer.read(Hibernate.unproxy(owner), fieldName);
        if (!(value instanceof Collection<?> collection)) {
            return new ChildFetch(0, List.of());
        }
        Hibernate.initialize(collection);
        List<Object> items = collection.stream().limit(maxRows).map(o -> (Object) o).toList();
        return new ChildFetch(collection.size(), items);
    }

    private EntityMetadata requireEnabled(String entityName) {
        EntityMetadata metadata = catalog.metadata(entityName);
        if (metadata == null) {
            throw new IllegalArgumentException("Unknown entity: " + entityName);
        }
        if (!catalog.enabled(entityName)) {
            throw new IllegalArgumentException("Entity is not enabled for querying: " + entityName);
        }
        if (metadata.idFieldName() == null) {
            throw new IllegalArgumentException("Entity has no single-attribute id and cannot be targeted: " + entityName);
        }
        return metadata;
    }

    private String key(String entityName, Object id) {
        return entityName + ":" + id;
    }
}
