package com.djt.jukeanator_engine;

import org.springframework.test.context.TestPropertySource;

/**
 * {@link AbstractSlaveModeApplicationTests} against the JPA repositories -- also runs the
 * outbox JPQL queries against the real schema. Requires a real MySQL server with a {@code
 * jukeanator_test} database the {@code jukeanator} user can access -- see {@code
 * src/test/resources/application-test.yml}. No Docker/Testcontainers dependency.
 */
@TestPropertySource(properties = { "app.repository-type=jpa" })
class MySqlSlaveModeJukeanatorEngineApplicationTests extends AbstractSlaveModeApplicationTests {
}
