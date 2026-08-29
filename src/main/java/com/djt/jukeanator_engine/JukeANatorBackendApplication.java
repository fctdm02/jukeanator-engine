package com.djt.jukeanator_engine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.djt.jukeanator_engine.config.ExternalConfigInitializer;

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

    // Points Spring's config loading at the WAR-relative config/ dir explicitly -- its own
    // default ./config/ lookup is relative to the process's working directory, which won't match
    // this directory unless the app happens to be launched from inside it. A --app.config-dir
    // program argument (e.g. for a kiosk deployment with config outside the install dir)
    // overrides that default location.
    Path configDirOverride = extractConfigDirOverride(args);
    Path externalConfigDir = ExternalConfigInitializer
        .seedExternalConfigIfAbsent(JukeANatorBackendApplication.class, configDirOverride);
    System.setProperty("spring.config.additional-location",
        "file:" + externalConfigDir.toAbsolutePath() + "/");

    SpringApplicationBuilder builder = new SpringApplicationBuilder(JukeANatorBackendApplication.class);

    builder.initializers(context -> {

      if (context.getEnvironment().getProperty("app.ui-enabled", Boolean.class, false)) {

        System.setProperty("java.awt.headless", "false");
      }
    });

    builder.run(args);
  }

  // Read ahead of Spring's own arg parsing, since the config directory has to be known before
  // Spring's config loading (which is what would otherwise expose this as a property) even starts.
  // Last occurrence wins, matching Spring's own precedence for repeated program arguments.
  private static Path extractConfigDirOverride(String[] args) {

    String prefix = "--app.config-dir=";
    return Arrays.stream(args).filter(arg -> arg.startsWith(prefix)).reduce((first, second) -> second)
        .map(arg -> Paths.get(arg.substring(prefix.length()))).orElse(null);
  }
}
