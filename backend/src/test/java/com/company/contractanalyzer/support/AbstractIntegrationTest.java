package com.company.contractanalyzer.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for Testcontainers-backed integration tests. Requires a running Docker engine.
 * All subclasses share one cached application context and one pgvector container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, TestModelConfig.class})
public abstract class AbstractIntegrationTest {
}
