package com.eventpulse.repository;

import com.eventpulse.domain.RegistrationStatus;

import java.time.Instant;

/** Slim projection so trend queries don't hydrate whole entities. */
public interface RegistrationTimestamp {

    Instant getRegisteredAt();

    RegistrationStatus getStatus();
}
