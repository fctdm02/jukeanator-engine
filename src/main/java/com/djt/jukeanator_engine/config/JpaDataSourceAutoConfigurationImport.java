package com.djt.jukeanator_engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Re-enables the DataSource/JPA/Flyway autoconfiguration that {@link
 * com.djt.jukeanator_engine.JukeANatorBackendApplication} excludes by default, but only on
 * instances that actually have {@code app.repository-type=jpa} set -- standalone/slave instances
 * default to {@code filesystem} and never touch a database, so this keeps {@code
 * DataSourceAutoConfiguration}/{@code HibernateJpaAutoConfiguration}/{@code
 * FlywayAutoConfiguration} from running -- and failing to reach a MySQL server -- on those.
 *
 * <p><b>Deliberately does not import {@code DataSourceTransactionManagerAutoConfiguration}</b>:
 * that registers a plain JDBC {@code DataSourceTransactionManager}, which doesn't synchronize a
 * JPA {@code EntityManager}'s own transaction state -- reads still appear to work against it, but
 * any {@code Query.executeUpdate()} (bulk/native UPDATE or DELETE) immediately throws {@code
 * TransactionRequiredException} ("No active transaction for update or delete query"), since that
 * check looks at the entity manager's own transaction, not just whether some transaction is open
 * on the underlying connection. {@code HibernateJpaAutoConfiguration} already registers its own
 * JPA-aware {@code JpaTransactionManager}; importing both created an unpredictable bean-priority
 * race that only surfaced against a real database, since this repository-type was never actually
 * exercised in CI/testing before Docker/a live MySQL instance was available.
 */
@Configuration
@ConditionalOnProperty(name = "app.repository-type", havingValue = "jpa")
@Import({ DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class })
public class JpaDataSourceAutoConfigurationImport {
}
