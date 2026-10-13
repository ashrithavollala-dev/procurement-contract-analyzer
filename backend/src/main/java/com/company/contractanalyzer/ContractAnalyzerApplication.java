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

    public static void main(String[] args) {
        SpringApplication.run(ContractAnalyzerApplication.class, args);
    }
}
