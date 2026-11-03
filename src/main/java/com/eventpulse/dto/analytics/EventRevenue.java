package com.eventpulse.dto.analytics;

import java.math.BigDecimal;

public record EventRevenue(Long eventId, String title, Long ticketsSold, BigDecimal revenue) {
}
