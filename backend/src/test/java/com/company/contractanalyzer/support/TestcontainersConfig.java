package com.company.contractanalyzer.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Throwaway pgvector PostgreSQL on a random host port (never 5432 or 5434). One container per
 * test JVM, shared by every cached Spring context; @ServiceConnection wires the DataSource.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    public static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return SharedPostgres.INSTANCE;
    }

    /** Lazily started singleton; Testcontainers' Ryuk removes it when the JVM exits. */
    public static final class SharedPostgres {
        public static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                .withDatabaseName("contract_analyzer_test")
                .withUsername("test")
                .withPassword("test");

        static {
            INSTANCE.start();
        }

        private SharedPostgres() {
        }
    }
}
