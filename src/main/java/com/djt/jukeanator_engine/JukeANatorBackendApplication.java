package com.djt.jukeanator_engine;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DataSource/JPA/Flyway autoconfiguration is excluded here and re-imported only on instances that
 * actually enable a JPA repository -- see {@code
 * com.djt.jukeanator_engine.config.JpaDataSourceAutoConfigurationImport}. Without this, Spring Boot
 * eagerly builds a MySQL connection pool and runs Flyway at startup even in standalone/filesystem
 * mode, which has no database to connect to.
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class })
@ConfigurationPropertiesScan
@EnableScheduling
public class JukeANatorBackendApplication {

  public static void main(String[] args) {

    SpringApplicationBuilder builder = new SpringApplicationBuilder(JukeANatorBackendApplication.class);

    builder.initializers(context -> {

      if (context.getEnvironment().getProperty("app.ui-enabled", Boolean.class, false)) {

        System.setProperty("java.awt.headless", "false");
      }
    });

    builder.run(args);
  }
}
