package com.djt.jukeanator_engine;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;

/**
 * Runs the {@code contextLoads} smoke test with the {@code mysql} profile explicitly activated
 * (JPA user repository, {@code db/migration/mysql} Flyway scripts) against a disposable
 * {@link MySqlTestcontainersConfiguration#mysqlContainer() MySQLContainer}, proving the schema and
 * datasource wiring both work against a real MySQL instance. Now that {@link
 * JukeanatorEngineApplicationTests} also provisions MySQL by default (via
 * {@link TestcontainersConfiguration}), this class is functionally equivalent to it; kept as a
 * separate class pending a future consolidation pass.
 *
 * <p>There is no embedded-database fallback here, so {@link #requiresDocker()} skips the whole
 * class instead of failing on a connection attempt to a MySQL instance that was never started.
 */
@Import(MySqlTestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
class MySqlJukeanatorEngineApplicationTests {

	@BeforeAll
	static void requiresDocker() {
		Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
				"Docker is required to run this test");
	}

	@Test
	void contextLoads() {
	}

}
