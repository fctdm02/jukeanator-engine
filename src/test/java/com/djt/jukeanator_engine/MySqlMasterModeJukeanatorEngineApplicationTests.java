package com.djt.jukeanator_engine;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

/**
 * Master-mode counterpart to {@link MySqlJukeanatorEngineApplicationTests}. {@code
 * location.repository-type} defaults to {@code filesystem} in every other test context, so {@code
 * LocationRepositoryJpaImpl} is never constructed there. Forcing {@code app.mode=master} and {@code
 * location.repository-type=jpa} here is what actually exercises {@code LocationEntity}'s JPA mapping
 * against the {@code db/migration/mysql/V2__init_location_schema.sql} schema (Hibernate {@code
 * ddl-auto: validate}, from the {@code mysql} profile), the same way
 * {@link MySqlJukeanatorEngineApplicationTests} already proves out {@code UserEntity} against
 * {@code V1__init_user_schema.sql}. {@code app.mode=master} is still set here so the master-only
 * beans that depend on {@code LocationService} (e.g. {@code LocationApiKeyAuthenticationFilter})
 * also get exercised.
 */
@Import(MySqlTestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@TestPropertySource(properties = { "app.mode=master", "location.repository-type=jpa" })
class MySqlMasterModeJukeanatorEngineApplicationTests {

	@BeforeAll
	static void requiresDocker() {
		Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
				"Docker is required to run this test");
	}

	@Test
	void contextLoads() {
	}

}
