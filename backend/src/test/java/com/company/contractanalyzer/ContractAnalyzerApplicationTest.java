package com.company.contractanalyzer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ContractAnalyzerApplicationTest {

    private final String originalConnect = System.getProperty(ContractAnalyzerApplication.CONNECT_TIMEOUT_PROPERTY);
    private final String originalRead = System.getProperty(ContractAnalyzerApplication.READ_TIMEOUT_PROPERTY);

    @AfterEach
    void restore() {
        restore(ContractAnalyzerApplication.CONNECT_TIMEOUT_PROPERTY, originalConnect);
        restore(ContractAnalyzerApplication.READ_TIMEOUT_PROPERTY, originalRead);
    }

    @Test
    void setsHttpTimeoutDefaultsWhenAbsent() {
        System.clearProperty(ContractAnalyzerApplication.CONNECT_TIMEOUT_PROPERTY);
        System.clearProperty(ContractAnalyzerApplication.READ_TIMEOUT_PROPERTY);

        ContractAnalyzerApplication.applyDefaultHttpTimeouts();

        assertThat(System.getProperty(ContractAnalyzerApplication.CONNECT_TIMEOUT_PROPERTY)).isEqualTo("30000");
        assertThat(System.getProperty(ContractAnalyzerApplication.READ_TIMEOUT_PROPERTY)).isEqualTo("120000");
    }

    @Test
    void keepsOperatorProvidedValues() {
        System.setProperty(ContractAnalyzerApplication.READ_TIMEOUT_PROPERTY, "999");

        ContractAnalyzerApplication.applyDefaultHttpTimeouts();

        assertThat(System.getProperty(ContractAnalyzerApplication.READ_TIMEOUT_PROPERTY)).isEqualTo("999");
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
