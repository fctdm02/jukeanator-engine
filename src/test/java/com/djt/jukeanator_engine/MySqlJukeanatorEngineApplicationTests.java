package com.djt.jukeanator_engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the {@code contextLoads} smoke test with the {@code mysql} profile explicitly activated
 * (JPA user repository, {@code db/migration/mysql} Flyway scripts) against a live local MySQL
 * instance (see {@code application.yml}'s {@code spring.datasource.*} defaults), proving the
 * schema and datasource wiring both work against a real MySQL instance. Now that {@link
 * JukeanatorEngineApplicationTests} also provisions MySQL by default (via
 * {@link TestcontainersConfiguration}), this class is functionally equivalent to it; kept as a
 * separate class pending a future consolidation pass.
 *
 * <p>Requires a real MySQL server with a {@code jukeanator_test} database the {@code jukeanator}
 * user can access -- see {@code src/test/resources/application-mysql.yml}. Deliberately a
 * separate database from {@code application.yml}'s own {@code jukeanator}, which is reserved for
 * manual QA against a master instance running locally. No Docker/Testcontainers dependency.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
class MySqlJukeanatorEngineApplicationTests {

	@Test
	void contextLoads() {
	}

}
