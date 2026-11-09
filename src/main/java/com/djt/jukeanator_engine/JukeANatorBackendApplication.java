package com.djt.jukeanator_engine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.djt.jukeanator_engine.config.ExternalConfigInitializer;
import com.djt.jukeanator_engine.config.ExternalConfigMerger;
import com.djt.jukeanator_engine.config.LegacyInstallMigrator;

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

  private static final Logger log = LoggerFactory.getLogger(JukeANatorBackendApplication.class);

  public static void main(String[] args) {

    // Points Spring's config loading at the WAR-relative config/ dir explicitly -- its own
    // default ./config/ lookup is relative to the process's working directory, which won't match
    // this directory unless the app happens to be launched from inside it. A --app.config-dir
    // program argument (e.g. for a kiosk deployment with config outside the install dir)
    // overrides that default location.
    Path configDirOverride =
        extractArgument(args, "--app.config-dir=").map(Paths::get).orElse(null);
    Path externalConfigDir = ExternalConfigInitializer
        .seedExternalConfigIfAbsent(JukeANatorBackendApplication.class, configDirOverride);
    Path externalConfigFile = externalConfigDir.resolve("application.yml");
    // Surfaces settings added by a newer build in the operator-edited file (behavior is
    // unchanged either way -- a missing key already falls back to the bundled value).
    ExternalConfigMerger.addMissingProperties(externalConfigFile);

    // One-time migration of a legacy install (C:\old_kiosk by default) -- before Spring starts,
    // so its license files, play counts and settings are in place for this start.
    Path legacyDir = extractArgument(args, "--app.legacy-dir=").map(Paths::get)
        .orElse(LegacyInstallMigrator.DEFAULT_LEGACY_DIR);
    Path dataDir = LegacyInstallMigrator
        .resolveDataDir(extractArgument(args, "--app.data-dir=").orElse(null), externalConfigFile);
    LegacyInstallMigrator.migrateIfNeeded(legacyDir, dataDir, externalConfigFile);
    System.setProperty("spring.config.additional-location",
        "file:" + externalConfigDir.toAbsolutePath() + "/");

    SpringApplicationBuilder builder = new SpringApplicationBuilder(JukeANatorBackendApplication.class);

    builder.initializers(context -> {

      // Logged as soon as the environment (including the external config dir above) is resolved,
      // so the effective value is visible regardless of which application.yml supplied it.
      log.info("mode: {}", context.getEnvironment().getProperty("app.mode"));
      log.info("repositoryType: {}", context.getEnvironment().getProperty("app.repository-type"));
      log.info("uiEnabled: {}", context.getEnvironment().getProperty("app.ui-enabled"));
      log.info("locationID: {}", context.getEnvironment().getProperty("app.location-id"));
      log.info("masterInstanceUrl: {}", context.getEnvironment().getProperty("app.master-instance-url"));

      if (context.getEnvironment().getProperty("app.ui-enabled", Boolean.class, false)) {

        System.setProperty("java.awt.headless", "false");
      }
    });

    builder.run(args);
  }

  // Read ahead of Spring's own arg parsing, since the config and data directories have to be known
  // before Spring's config loading (which is what would otherwise expose them as properties) even
  // starts. Last occurrence wins, matching Spring's own precedence for repeated program arguments.
  private static Optional<String> extractArgument(String[] args, String prefix) {

    return Arrays.stream(args).filter(arg -> arg.startsWith(prefix)).reduce((first, second) -> second)
        .map(arg -> arg.substring(prefix.length()));
  }
}
