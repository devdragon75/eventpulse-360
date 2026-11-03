package com.eventpulse.dto.analytics;

import java.math.BigDecimal;

/** Query projection: net-of-refund revenue per ticket type. */
public record TicketTypeRevenue(Long ticketTypeId, BigDecimal revenue) {
}
