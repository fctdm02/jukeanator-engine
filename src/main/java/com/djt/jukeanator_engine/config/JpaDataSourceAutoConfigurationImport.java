package com.djt.jukeanator_engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Re-enables the DataSource/JPA/Flyway autoconfiguration that {@link
 * com.djt.jukeanator_engine.JukeANatorBackendApplication} excludes by default, but only on
 * instances that actually have {@code app.repository-type=jpa} set -- standalone/slave instances
 * default to {@code filesystem} and never touch a database, so this keeps {@code
 * DataSourceAutoConfiguration}/{@code HibernateJpaAutoConfiguration}/{@code
 * FlywayAutoConfiguration} from running -- and failing to reach a MySQL server -- on those.
 */
@Configuration
@ConditionalOnProperty(name = "app.repository-type", havingValue = "jpa")
@Import({ DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class })
public class JpaDataSourceAutoConfigurationImport {
}
