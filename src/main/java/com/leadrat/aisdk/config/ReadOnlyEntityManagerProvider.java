package com.leadrat.aisdk.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.metamodel.ManagedType;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ReadOnlyEntityManagerProvider implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyEntityManagerProvider.class);

    private static final int SCAN_ROOT_DEPTH = 3;

    private static final List<String> EXCLUDED_PROPERTY_PREFIXES = List.of(
            "hibernate.connection",
            "hibernate.hikari",
            "hibernate.c3p0",
            "hibernate.proxool",
            "hibernate.hbm2ddl",
            "hibernate.transaction",
            "hibernate.jta",
            "hibernate.current_session_context_class",
            "hibernate.temp",
            "hibernate.ejb.persistenceUnitName",
            "hibernate.session_factory_name",
            "jakarta.persistence.jdbc",
            "jakarta.persistence.jtaDataSource",
            "jakarta.persistence.nonJtaDataSource",
            "jakarta.persistence.dataSource",
            "jakarta.persistence.transactionType",
            "jakarta.persistence.schema-generation",
            "jakarta.persistence.sql-load-script-source",
            "jakarta.persistence.sharedCache");

    private final EntityManagerFactory factory;
    private final EntityManagerFactory hostFactory;
    private final ReadOnlyDataSource readOnlyDataSource;
    private LocalContainerEntityManagerFactoryBean ownedFactoryBean;

    public ReadOnlyEntityManagerProvider(AiSdkProperties properties, EntityManagerFactory hostFactory,
                                         DataSource hostDataSource) {
        this.hostFactory = hostFactory;
        this.readOnlyDataSource = new ReadOnlyDataSource(hostDataSource,
                properties.getQuery().getDbTimeoutSeconds() * 1000);
        this.factory = build(properties, hostFactory);
    }

    public EntityManager createEntityManager() {
        EntityManager em = factory.createEntityManager();
        em.setFlushMode(FlushModeType.COMMIT);
        try {
            Session session = em.unwrap(Session.class);
            session.setDefaultReadOnly(true);
            session.setHibernateFlushMode(org.hibernate.FlushMode.MANUAL);
        } catch (RuntimeException e) {
            log.debug("ai-sdk: could not mark Hibernate session read-only ({})", e.toString());
        }
        return em;
    }

    public EntityManagerFactory factory() {
        return factory;
    }

    public EntityManagerFactory hostFactory() {
        return hostFactory;
    }

    public DataSource readOnlyDataSource() {
        return readOnlyDataSource;
    }

    public String enforcement() {
        return "application-guardrail";
    }

    private EntityManagerFactory build(AiSdkProperties properties, EntityManagerFactory hostFactory) {
        TreeSet<String> managed = new TreeSet<>();
        for (ManagedType<?> type : hostFactory.getMetamodel().getManagedTypes()) {
            if (type.getJavaType() != null) {
                managed.add(type.getJavaType().getName());
            }
        }

        Map<String, Object> jpa = new HashMap<>();
        hostFactory.getProperties().forEach((key, value) -> {
            if (value != null && isCopyable(key)) {
                jpa.put(key, value);
            }
        });
        jpa.put("hibernate.hbm2ddl.auto", "none");
        jpa.put("hibernate.connection.autocommit", "true");
        jpa.put("hibernate.jdbc.batch_size", 0);
        jpa.put("hibernate.order_inserts", false);
        jpa.put("hibernate.order_updates", false);
        jpa.put("org.hibernate.flushMode", "MANUAL");
        jpa.put("hibernate.query.in_clause_parameter_padding", true);
        jpa.put("jakarta.persistence.query.timeout", properties.getQuery().getDbTimeoutSeconds() * 1000);

        String[] scanRoots = scanRoots(managed);
        log.info("ai-sdk: building the read-only persistence unit from {} host managed types, scanning {}",
                managed.size(), Arrays.toString(scanRoots));

        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setPersistenceUnitName("aiSdkReadOnly");
        bean.setPackagesToScan(scanRoots);
        bean.setDataSource(readOnlyDataSource);
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        bean.setJpaPropertyMap(jpa);
        bean.setPersistenceUnitPostProcessors(unit -> {
            unit.setExcludeUnlistedClasses(true);
            List<String> listed = unit.getManagedClassNames();
            managed.stream().filter(name -> !listed.contains(name)).forEach(unit::addManagedClassName);
        });
        bean.afterPropertiesSet();
        this.ownedFactoryBean = bean;
        log.info("ai-sdk: read-only EntityManagerFactory bound to the application DataSource through the read-only "
                + "guardrail ({} managed types); sessions run with SELECT-only SQL validation, a read-only JDBC "
                + "session and a {}s statement timeout", managed.size(), properties.getQuery().getDbTimeoutSeconds());
        return bean.getObject();
    }

    private String[] scanRoots(TreeSet<String> managed) {
        TreeSet<String> roots = new TreeSet<>();
        for (String className : managed) {
            String[] segments = className.split("\\.");
            int depth = Math.min(SCAN_ROOT_DEPTH, segments.length - 1);
            if (depth > 0) {
                roots.add(String.join(".", Arrays.copyOfRange(segments, 0, depth)));
            }
        }
        List<String> candidates = List.copyOf(roots);
        roots.removeIf(root -> candidates.stream().anyMatch(other -> !other.equals(root) && root.startsWith(other + ".")));
        if (roots.isEmpty()) {
            roots.add(getClass().getPackageName());
        }
        return roots.toArray(String[]::new);
    }

    private boolean isCopyable(String key) {
        if (key == null) {
            return false;
        }
        for (String prefix : EXCLUDED_PROPERTY_PREFIXES) {
            if (key.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void destroy() {
        if (ownedFactoryBean != null) {
            ownedFactoryBean.destroy();
        }
    }
}
