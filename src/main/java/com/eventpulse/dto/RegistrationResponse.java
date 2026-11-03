package com.eventpulse.dto;

import com.eventpulse.domain.PaymentStatus;
import com.eventpulse.domain.Registration;
import com.eventpulse.domain.RegistrationStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RegistrationResponse(Long id, Long eventId, String eventTitle, Long ticketTypeId, String ticketType,
                                   Long attendeeId, String attendeeName, String attendeeEmail,
                                   RegistrationStatus status, String ticketCode, BigDecimal amount,
                                   PaymentStatus paymentStatus, Instant registeredAt, Instant cancelledAt) {

    public static RegistrationResponse from(Registration r, PaymentStatus paymentStatus) {
        return new RegistrationResponse(r.getId(), r.getEvent().getId(), r.getEvent().getTitle(),
                r.getTicketType().getId(), r.getTicketType().getName(), r.getAttendee().getId(),
                r.getAttendee().getName(), r.getAttendee().getEmail(), r.getStatus(), r.getTicketCode(),
                r.getAmount(), paymentStatus, r.getRegisteredAt(), r.getCancelledAt());
    }
}
