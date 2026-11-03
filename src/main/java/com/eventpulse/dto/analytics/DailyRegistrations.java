package com.eventpulse.dto.analytics;

import java.time.LocalDate;

public record DailyRegistrations(LocalDate date, long registrations, long cancelled) {
}
