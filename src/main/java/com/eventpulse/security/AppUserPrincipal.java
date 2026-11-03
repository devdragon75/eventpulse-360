package com.eventpulse.security;

import com.eventpulse.domain.Role;

/** The authenticated caller, rebuilt from JWT claims on every request (no DB lookup). */
public record AppUserPrincipal(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Organizers see only their own data; admins see everything ({@code null} = no filter). */
    public Long organizerScope() {
        return isAdmin() ? null : id;
    }
}
