package com.leadrat.aisdk.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.ManagedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public class ReadOnlyEntityManagerProvider implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyEntityManagerProvider.class);

    private final EntityManagerFactory factory;
    private final EntityManagerFactory hostFactory;
    private final boolean dedicated;
    private HikariDataSource ownedDataSource;
    private LocalContainerEntityManagerFactoryBean ownedFactoryBean;

    public ReadOnlyEntityManagerProvider(AiSdkProperties properties, EntityManagerFactory hostFactory) {
        this.hostFactory = hostFactory;
        AiSdkProperties.Datasource.ReadOnly ro = properties.getDatasource().getReadonly();
        if (StringUtils.hasText(ro.getUrl())) {
            this.factory = buildDedicated(ro, properties, hostFactory);
            this.dedicated = true;
        } else {
            log.warn("ai-sdk: no read-only datasource configured (ai-sdk.datasource.readonly.url is blank). "
                    + "Falling back to the application's primary DataSource. This is NOT the recommended production posture: "
                    + "configure a dedicated SELECT-only PostgreSQL role.");
            this.factory = hostFactory;
            this.dedicated = false;
        }
    }

    public EntityManager createEntityManager() {
        return factory.createEntityManager();
    }

    public EntityManagerFactory factory() {
        return factory;
    }

    public EntityManagerFactory hostFactory() {
        return hostFactory;
    }

    public boolean isDedicated() {
        return dedicated;
    }

    private EntityManagerFactory buildDedicated(AiSdkProperties.Datasource.ReadOnly ro,
                                                AiSdkProperties properties,
                                                EntityManagerFactory hostFactory) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(ro.getUrl());
        ds.setUsername(ro.getUsername());
        ds.setPassword(ro.getPassword());
        ds.setReadOnly(true);
        ds.setAutoCommit(true);
        ds.setPoolName("ai-sdk-readonly");
        ds.setMaximumPoolSize(5);
        this.ownedDataSource = ds;

        TreeSet<String> managed = new TreeSet<>();
        for (ManagedType<?> type : hostFactory.getMetamodel().getManagedTypes()) {
            if (type.getJavaType() != null) {
                managed.add(type.getJavaType().getName());
            }
        }

        Map<String, Object> jpa = new HashMap<>();
        jpa.put("hibernate.hbm2ddl.auto", "none");
        jpa.put("hibernate.connection.autocommit", "true");
        jpa.put("jakarta.persistence.query.timeout", properties.getQuery().getDbTimeoutSeconds() * 1000);

        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setPersistenceUnitName("aiSdkReadOnly");
        bean.setDataSource((DataSource) ds);
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        bean.setJpaPropertyMap(jpa);
        bean.setPersistenceUnitPostProcessors(unit -> {
            unit.setExcludeUnlistedClasses(true);
            managed.forEach(unit::addManagedClassName);
        });
        bean.afterPropertiesSet();
        this.ownedFactoryBean = bean;
        log.info("ai-sdk: read-only EntityManagerFactory bound to dedicated datasource with {} managed types", managed.size());
        return bean.getObject();
    }

    @Override
    public void destroy() {
        if (ownedFactoryBean != null) {
            ownedFactoryBean.destroy();
        }
        if (ownedDataSource != null) {
            ownedDataSource.close();
        }
    }
}
