package com.eventpulse.dto;

import com.eventpulse.domain.TicketType;

import java.math.BigDecimal;

public record TicketTypeResponse(Long id, Long eventId, String name, BigDecimal price, int quantity, int sold,
                                 int remaining) {

    public static TicketTypeResponse from(TicketType t) {
        return new TicketTypeResponse(t.getId(), t.getEvent().getId(), t.getName(), t.getPrice(),
                t.getQuantity(), t.getSold(), t.remaining());
    }
}
