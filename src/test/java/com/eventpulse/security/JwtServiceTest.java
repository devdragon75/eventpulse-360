package com.eventpulse.security;

import com.eventpulse.config.AppProperties;
import com.eventpulse.domain.Role;
import com.eventpulse.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private JwtService jwt;
    private User user;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(props(SECRET));
        user = new User("Olivia Organizer", "olivia@example.com", "hash", Role.ORGANIZER);
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    @Test
    void roundTripsIdentityAndRole() {
        String token = jwt.generateToken(user, Instant.now());

        AppUserPrincipal principal = jwt.parse(token).orElseThrow();

        assertThat(principal.id()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("olivia@example.com");
        assertThat(principal.role()).isEqualTo(Role.ORGANIZER);
        assertThat(principal.organizerScope()).isEqualTo(42L);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.generateToken(user, Instant.now());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(jwt.parse(tampered)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        String token = jwt.generateToken(user, Instant.now().minus(Duration.ofHours(2)));

        assertThat(jwt.parse(token)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = new JwtService(props("another-secret-another-secret-another-1")).generateToken(user, Instant.now());

        assertThat(jwt.parse(foreign)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesWeakSecret() {
        assertThatThrownBy(() -> new JwtService(props("too-short")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AppProperties props(String secret) {
        return new AppProperties(new AppProperties.Jwt(secret, Duration.ofMinutes(5)), null, null);
    }
}
