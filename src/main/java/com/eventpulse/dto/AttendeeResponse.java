package com.eventpulse.dto;

import com.eventpulse.domain.Attendee;

import java.time.Instant;

public record AttendeeResponse(Long id, String name, String email, Instant createdAt) {

    public static AttendeeResponse from(Attendee a) {
        return new AttendeeResponse(a.getId(), a.getName(), a.getEmail(), a.getCreatedAt());
    }
}
