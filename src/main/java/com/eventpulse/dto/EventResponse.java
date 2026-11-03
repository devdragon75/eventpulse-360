package com.eventpulse.dto;

import com.eventpulse.domain.Event;
import com.eventpulse.domain.EventStatus;

import java.time.Instant;

public record EventResponse(Long id, String title, String description, String venue, int capacity,
                            Instant startsAt, Instant endsAt, EventStatus status, Long organizerId,
                            String organizerName) {

    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getVenue(), e.getCapacity(),
                e.getStartsAt(), e.getEndsAt(), e.getStatus(), e.getOrganizer().getId(),
                e.getOrganizer().getName());
    }
}
