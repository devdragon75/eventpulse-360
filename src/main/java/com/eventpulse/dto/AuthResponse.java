package com.eventpulse.dto;

import com.eventpulse.domain.Role;

import java.time.Instant;

public record AuthResponse(String token, String tokenType, Instant expiresAt, Long userId, String name, Role role) {
}
