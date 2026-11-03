package com.eventpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "eventpulse")
public record AppProperties(Jwt jwt, Admin admin, Kafka kafka) {

    public record Jwt(String secret, Duration expiration) {
    }

    public record Admin(String name, String email, String password) {
    }

    public record Kafka(boolean enabled, String registrationTopic) {
    }
}
