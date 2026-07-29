package com.djt.jukeanator_engine;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;

/**
 * MySQL counterpart to {@link JukeanatorEngineApplicationTests}. Activates the {@code mysql}
 * profile (JPA user repository, {@code db/migration/mysql} Flyway scripts) against a disposable
 * {@link MySqlTestcontainersConfiguration#mysqlContainer() MySQLContainer} instead of the default
 * Postgres one, proving the schema and datasource wiring both work against a real MySQL instance.
 *
 * <p>Unlike {@link JukeanatorEngineApplicationTests}, the {@code mysql} profile hardcodes a real
 * {@code jdbc:mysql://} URL (see {@code application-mysql.yml}) rather than falling back to an
 * embedded database, so there's nothing for Spring to fall back to if Docker isn't available.
 * {@link #requiresDocker()} skips the whole class in that case instead of failing on a connection
 * attempt to a MySQL instance that was never started.
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
