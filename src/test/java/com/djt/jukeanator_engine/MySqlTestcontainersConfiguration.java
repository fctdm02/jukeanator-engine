package com.djt.jukeanator_engine;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL Testcontainers configuration used by the {@code mysql}-profile test variants in this
 * package. Functionally equivalent to {@link TestcontainersConfiguration} -- both provision a
 * disposable MySQL container via {@code @ServiceConnection} -- but kept as a separate class so
 * the {@code mysql}-profile test classes can import it explicitly, independent of the default
 * test configuration; the two are never imported together, since Spring Boot only supports one
 * JDBC {@code @ServiceConnection} per test context.
 */
@TestConfiguration(proxyBeanMethods = false)
class MySqlTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	@Conditional(DockerAvailableCondition.class)
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:latest"));
	}

}
