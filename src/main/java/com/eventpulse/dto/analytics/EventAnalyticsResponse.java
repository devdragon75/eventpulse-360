package com.eventpulse.dto.analytics;

import com.eventpulse.domain.EventStatus;

import java.math.BigDecimal;
import java.util.List;

public record EventAnalyticsResponse(Long eventId, String title, EventStatus status, int capacity,
                                     int ticketsAllocated, int ticketsSold, double occupancyPercent,
                                     double sellThroughPercent, long confirmedRegistrations,
                                     long cancelledRegistrations, BigDecimal grossRevenue, BigDecimal refunded,
                                     BigDecimal netRevenue, List<TicketTypeSales> byTicketType) {
}
