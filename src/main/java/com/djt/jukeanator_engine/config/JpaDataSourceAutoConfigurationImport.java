package com.djt.jukeanator_engine.config;

import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Re-enables the DataSource/JPA/Flyway autoconfiguration that {@link
 * com.djt.jukeanator_engine.JukeANatorBackendApplication} excludes by default, but only on
 * instances that actually have a JPA repository turned on. See {@link
 * JpaRepositoryRequiredCondition}.
 */
@Configuration
@Conditional(JpaRepositoryRequiredCondition.class)
@Import({ DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class })
public class JpaDataSourceAutoConfigurationImport {
}
