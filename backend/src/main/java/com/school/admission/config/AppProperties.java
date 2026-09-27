package com.school.admission.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/** Bound from the "admission" section of application.yml. */
@ConfigurationProperties(prefix = "admission")
public record AppProperties(String uploadDir, Payments payments, DevTools devTools) {

    public record Payments(String defaultGateway, Map<String, String> webhookSecrets) { }

    public record DevTools(boolean enabled) { }
}
