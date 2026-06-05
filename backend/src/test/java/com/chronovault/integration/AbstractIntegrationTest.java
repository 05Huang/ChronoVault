package com.chronovault.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for integration tests using Testcontainers.
 * Provides PostgreSQL and Redis containers that are started once per test class
 * and shared across all test methods.
 *
 * Subclasses inherit the full Spring Boot context with real database and Redis.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("chronovault")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    protected static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Create a unique database for this test run to avoid schema conflicts
        String dbUrl = postgres.getJdbcUrl();
        String uniqueDb = "cv_test_" + System.nanoTime();
        try (var conn = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl().replace("chronovault", "postgres"),
                postgres.getUsername(), postgres.getPassword());
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + uniqueDb);
        } catch (Exception e) {
            // Ignore - database might already exist
        }
        registry.add("spring.datasource.url", () ->
                postgres.getJdbcUrl().replace("chronovault", uniqueDb));

        // PostgreSQL
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Disable Flyway and SQL init — use Hibernate DDL to avoid jsonb/String type mismatch
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // ChronoVault config
        registry.add("chronovault.master-key", () -> "integration-test-master-key-at-least-32-chars!!!");
        registry.add("chronovault.restic-password", () -> "integration-test-restic-password");
        registry.add("chronovault.ssh.connection-timeout", () -> 5000);
        registry.add("chronovault.ssh.command-timeout", () -> 10000);
        registry.add("chronovault.ssh.max-retry", () -> 1);
        registry.add("chronovault.ai.enabled", () -> false);

        // JWT config
        registry.add("jwt.secret", () -> "integration-test-jwt-secret-key-at-least-32-chars-for-hs256!!!");
        registry.add("jwt.expiration", () -> 3600000L);
    }
}
