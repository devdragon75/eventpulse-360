package com.eventpulse.messaging;

import com.eventpulse.domain.Registration;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Business event emitted whenever a registration changes state. It is both the in-process Spring event
 * and the JSON payload on the Kafka topic, which is the Bronze layer's input contract.
 */
public record RegistrationEvent(RegistrationEventType eventType, Long registrationId, Long eventId,
                                String eventTitle, Long ticketTypeId, String ticketType, Long attendeeId,
                                String attendeeEmail, BigDecimal amount, String currency, Instant occurredAt) {

    public static RegistrationEvent of(RegistrationEventType type, Registration r) {
        return new RegistrationEvent(type, r.getId(), r.getEvent().getId(), r.getEvent().getTitle(),
                r.getTicketType().getId(), r.getTicketType().getName(), r.getAttendee().getId(),
                r.getAttendee().getEmail(), r.getAmount(), "USD", Instant.now());
    }
}
