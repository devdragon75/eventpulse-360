package com.eventpulse.controller;

import com.eventpulse.dto.analytics.DailyRegistrations;
import com.eventpulse.dto.analytics.EventAnalyticsResponse;
import com.eventpulse.dto.analytics.OverviewResponse;
import com.eventpulse.security.AppUserPrincipal;
import com.eventpulse.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@Validated
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Operation(summary = "Revenue and registration totals across my events (all events for admins)")
    @GetMapping("/overview")
    public OverviewResponse overview(@AuthenticationPrincipal AppUserPrincipal caller) {
        return analytics.overview(caller);
    }

    @Operation(summary = "Capacity, sell-through and revenue for one event")
    @GetMapping("/events/{id}")
    public EventAnalyticsResponse event(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal caller) {
        return analytics.eventAnalytics(id, caller);
    }

    @Operation(summary = "Daily registration trend for one event")
    @GetMapping("/events/{id}/registrations/daily")
    public List<DailyRegistrations> daily(@PathVariable Long id,
                                          @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days,
                                          @AuthenticationPrincipal AppUserPrincipal caller) {
        return analytics.dailyRegistrations(id, days, caller);
    }
}
