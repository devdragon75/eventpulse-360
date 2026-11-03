package com.eventpulse.dto.analytics;

import java.math.BigDecimal;

public record TicketTypeSales(Long ticketTypeId, String name, BigDecimal price, int quantity, int sold,
                              BigDecimal revenue) {
}
