package com.djt.jukeanator_engine;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL counterpart to {@link TestcontainersConfiguration}. Import this instead when a test
 * needs to run against the {@code mysql} profile (see {@code application-mysql.yml}) rather than
 * the default Postgres container -- the two are never imported together, since Spring Boot only
 * supports one JDBC {@code @ServiceConnection} per test context.
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
