package com.company.contractanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Agentic Procurement Contract Analyzer.
 * Portfolio/demo system: no authentication or multi-tenancy, not for untrusted public access.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ContractAnalyzerApplication {

    /**
     * JDK-wide defaults for HttpURLConnection, which has no timeouts by default. Spring AI
     * downloads the ONNX embedding model on first start through UrlResource without
     * timeouts, so a stalled connection would otherwise block startup forever. With these
     * defaults the download fails and the container restart policy retries it.
     */
    static final String CONNECT_TIMEOUT_PROPERTY = "sun.net.client.defaultConnectTimeout";
    static final String READ_TIMEOUT_PROPERTY = "sun.net.client.defaultReadTimeout";

    public static void main(String[] args) {
        applyDefaultHttpTimeouts();
        SpringApplication.run(ContractAnalyzerApplication.class, args);
    }

    /** Sets the defaults only when the operator has not set them (e.g. via JAVA_TOOL_OPTIONS). */
    static void applyDefaultHttpTimeouts() {
        if (System.getProperty(CONNECT_TIMEOUT_PROPERTY) == null) {
            System.setProperty(CONNECT_TIMEOUT_PROPERTY, "30000");
        }
        if (System.getProperty(READ_TIMEOUT_PROPERTY) == null) {
            System.setProperty(READ_TIMEOUT_PROPERTY, "120000");
        }
    }
}
