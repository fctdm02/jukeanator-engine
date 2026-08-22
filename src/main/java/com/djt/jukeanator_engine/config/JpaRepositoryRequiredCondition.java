package com.djt.jukeanator_engine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;

/**
 * Matches when {@code user.repository-type}, {@code location.repository-type}, or {@code
 * song-queue.repository-type} is set to {@code jpa} -- i.e. when something in this instance
 * actually needs the MySQL-backed JPA stack. ({@code song-library.repository-type} is
 * deliberately not checked here -- see {@code SongLibraryRepositoryJpaImpl}'s class javadoc.)
 *
 * <p>Standalone/slave instances default all to {@code filesystem} and never touch a database; a
 * master instance (see {@code docs/application-master-mode.yml}) sets {@code
 * location.repository-type=jpa}. Gating {@link JpaDataSourceAutoConfigurationImport} on this
 * condition keeps {@code DataSourceAutoConfiguration}/{@code HibernateJpaAutoConfiguration}/{@code
 * FlywayAutoConfiguration} from running -- and failing to reach a MySQL server -- on instances that
 * have no JPA repository enabled at all.
 */
public class JpaRepositoryRequiredCondition extends AnyNestedCondition {

  public JpaRepositoryRequiredCondition() {
    super(ConfigurationPhase.PARSE_CONFIGURATION);
  }

  @ConditionalOnProperty(name = "user.repository-type", havingValue = "jpa")
  static class UserRepositoryIsJpa {
  }

  @ConditionalOnProperty(name = "location.repository-type", havingValue = "jpa")
  static class LocationRepositoryIsJpa {
  }

  @ConditionalOnProperty(name = "song-queue.repository-type", havingValue = "jpa")
  static class SongQueueRepositoryIsJpa {
  }
}
