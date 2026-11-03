package com.eventpulse.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record OverviewResponse(long totalEvents, long publishedEvents, long confirmedRegistrations,
                               BigDecimal grossRevenue, BigDecimal refunded, BigDecimal netRevenue,
                               List<EventRevenue> topEventsByRevenue) {
}
