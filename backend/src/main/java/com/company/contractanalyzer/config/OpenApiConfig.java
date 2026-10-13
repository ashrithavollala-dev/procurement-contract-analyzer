package com.company.contractanalyzer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata; endpoints are documented from the controllers (see docs/API_CONTRACT.md). */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI contractAnalyzerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agentic Procurement Contract Analyzer API")
                        .version("v1")
                        .description("Contract ingestion, semantic clause search, agentic compliance audits and "
                                + "grounded Q&A. Demo system: no authentication, not for untrusted public access. "
                                + "The authoritative contract is docs/API_CONTRACT.md.")
                        .license(new License().name("Portfolio demo - fictional data only")))
                .servers(List.of(new Server().url("/").description("Same origin")));
    }
}
