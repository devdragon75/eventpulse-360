package com.eventpulse.service;

import com.eventpulse.domain.Role;
import com.eventpulse.domain.User;
import com.eventpulse.dto.AuthResponse;
import com.eventpulse.dto.LoginRequest;
import com.eventpulse.dto.SignupRequest;
import com.eventpulse.exception.EmailAlreadyUsedException;
import com.eventpulse.repository.UserRepository;
import com.eventpulse.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Public sign-up always creates an ORGANIZER; admins are provisioned out of band. */
    @Transactional
    public AuthResponse signup(SignupRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        try {
            User user = users.saveAndFlush(new User(req.name().trim(), email, encoder.encode(req.password()), Role.ORGANIZER));
            return toResponse(user);
        } catch (DataIntegrityViolationException e) { // lost a race with a concurrent sign-up
            throw new EmailAlreadyUsedException();
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        // Same error for "unknown user" and "wrong password" so accounts can't be enumerated.
        User user = users.findByEmail(req.email().trim().toLowerCase())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        Instant now = Instant.now();
        return new AuthResponse(jwt.generateToken(user, now), "Bearer", jwt.expiryFrom(now), user.getId(),
                user.getName(), user.getRole());
    }
}
