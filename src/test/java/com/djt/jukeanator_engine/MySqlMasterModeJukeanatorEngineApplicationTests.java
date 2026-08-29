package com.djt.jukeanator_engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Master-mode counterpart to {@link MySqlJukeanatorEngineApplicationTests}. {@code
 * app.repository-type} defaults to {@code filesystem} in every other test context, so {@code
 * LocationRepositoryJpaImpl} is never constructed there. Forcing {@code app.mode=master} and
 * {@code app.repository-type=jpa} here is what actually exercises {@code LocationEntity}'s JPA
 * mapping against the {@code db/migration/mysql/V7__rename_locations_to_location_and_add_fields.sql}
 * schema (Hibernate {@code ddl-auto: validate}, from the {@code mysql} profile), the same way
 * {@link MySqlJukeanatorEngineApplicationTests} already proves out {@code UserEntity} against
 * {@code V1__init_user_schema.sql}. {@code app.mode=master} is still set here so the master-only
 * beans that depend on {@code LocationService} (e.g. {@code LocationApiKeyAuthenticationFilter})
 * also get exercised.
 *
 * <p>Requires a real MySQL server with a {@code jukeanator_test} database the {@code jukeanator}
 * user can access -- see {@code src/test/resources/application-mysql.yml}. Deliberately a
 * separate database from {@code application.yml}'s own {@code jukeanator}, which is reserved for
 * manual QA against a master instance running locally. No Docker/Testcontainers dependency.
 */
@SpringBootTest
@ActiveProfiles({ "test", "mysql" })
@TestPropertySource(properties = { "app.mode=master", "app.repository-type=jpa" })
class MySqlMasterModeJukeanatorEngineApplicationTests {

	@Test
	void contextLoads() {
	}

}
