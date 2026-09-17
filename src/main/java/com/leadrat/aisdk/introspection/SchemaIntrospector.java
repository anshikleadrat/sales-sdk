package com.leadrat.aisdk.introspection;

import com.leadrat.aisdk.configure.ConfigRepository;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SchemaIntrospector {

    private static final Logger log = LoggerFactory.getLogger(SchemaIntrospector.class);

    private final EntityManagerFactory hostFactory;
    private final ConfigRepository configRepository;
    private volatile Map<String, EntityMetadata> cache = Map.of();

    public SchemaIntrospector(EntityManagerFactory hostFactory, ConfigRepository configRepository) {
        this.hostFactory = hostFactory;
        this.configRepository = configRepository;
    }

    public synchronized int scan() {
        Map<String, EntityMetadata> discovered = new LinkedHashMap<>();
        for (EntityType<?> entityType : hostFactory.getMetamodel().getEntities()) {
            EntityMetadata metadata = describe(entityType);
            discovered.put(metadata.entityName(), metadata);
        }
        this.cache = discovered;
        configRepository.persistDiscovery(new ArrayList<>(discovered.values()));
        log.info("ai-sdk: introspection complete, {} entities discovered", discovered.size());
        return discovered.size();
    }

    public Map<String, EntityMetadata> metadata() {
        if (cache.isEmpty()) {
            scan();
        }
        return cache;
    }

    public EntityMetadata metadataFor(String entityName) {
        return metadata().get(entityName);
    }

    private EntityMetadata describe(EntityType<?> entityType) {
        Class<?> javaType = entityType.getJavaType();
        String entityName = entityType.getName();
        List<EntityMetadata.DiscoveredField> fields = new ArrayList<>();
        List<EntityMetadata.DiscoveredRelationship> relationships = new ArrayList<>();

        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            Attribute.PersistentAttributeType kind = attribute.getPersistentAttributeType();
            if (kind == Attribute.PersistentAttributeType.BASIC
                    || kind == Attribute.PersistentAttributeType.EMBEDDED
                    || kind == Attribute.PersistentAttributeType.ELEMENT_COLLECTION) {
                fields.add(new EntityMetadata.DiscoveredField(attribute.getName(), attribute.getJavaType().getSimpleName()));
                continue;
            }
            String relationshipType = RelationshipClassifier.type(kind);
            Class<?> related = attribute instanceof PluralAttribute<?, ?, ?> plural
                    ? plural.getElementType().getJavaType()
                    : attribute.getJavaType();
            String relatedEntityName = entityNameOf(related);
            relationships.add(new EntityMetadata.DiscoveredRelationship(
                    attribute.getName(),
                    relatedEntityName,
                    related,
                    relationshipType,
                    RelationshipClassifier.direction(relationshipType),
                    RelationshipClassifier.mappedBy(javaType, attribute.getName())));
        }

        String idField = null;
        Class<?> idType = null;
        if (entityType.hasSingleIdAttribute()) {
            SingularAttribute<?, ?> id = entityType.getId(entityType.getIdType().getJavaType());
            idField = id.getName();
            idType = id.getJavaType();
        }

        return new EntityMetadata(entityName, tableNameOf(javaType, entityName), javaType, idField, idType, fields, relationships);
    }

    private String entityNameOf(Class<?> javaType) {
        for (EntityType<?> type : hostFactory.getMetamodel().getEntities()) {
            if (type.getJavaType() != null && type.getJavaType().equals(javaType)) {
                return type.getName();
            }
        }
        return javaType.getSimpleName();
    }

    private String tableNameOf(Class<?> javaType, String entityName) {
        Table table = javaType.getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name();
        }
        return entityName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
