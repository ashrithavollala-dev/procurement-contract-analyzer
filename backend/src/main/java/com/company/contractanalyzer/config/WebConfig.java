package com.company.contractanalyzer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for direct browser access during development. In the normal setups the browser talks to
 * the same origin (Vite proxy or Nginx reverse proxy), so CORS is not involved.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.getCors().getAllowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getCors().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
