package edu.gatech.erms.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One MySQL 8.4 container shared by every integration-test context in the JVM (singleton pattern).
 * Spring Boot wires the datasource to it via {@link ServiceConnection}; Flyway then migrates it
 * exactly as in production.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  public static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4");

  private static final MySQLContainer MYSQL = new MySQLContainer(MYSQL_IMAGE);

  @Bean
  @ServiceConnection
  MySQLContainer mysqlContainer() {
    return MYSQL;
  }
}
