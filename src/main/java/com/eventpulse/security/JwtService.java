package com.eventpulse.security;

import com.eventpulse.config.AppProperties;
import com.eventpulse.domain.Role;
import com.eventpulse.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(AppProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("eventpulse.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expiration = props.jwt().expiration();
    }

    public Instant expiryFrom(Instant issuedAt) {
        return issuedAt.plus(expiration);
    }

    public String generateToken(User user, Instant issuedAt) {
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiryFrom(issuedAt)))
                .signWith(key)
                .compact();
    }

    /** Returns empty for any invalid, tampered or expired token. */
    public Optional<AppUserPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AppUserPrincipal(Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class), Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
