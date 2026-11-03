package com.eventpulse.service;

import com.eventpulse.config.DemoDataSeeder;
import com.eventpulse.domain.EventStatus;
import com.eventpulse.repository.EventRepository;
import com.eventpulse.repository.TicketTypeRepository;
import com.eventpulse.repository.UserRepository;
import com.eventpulse.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=10000")
@ActiveProfiles("demo")
class DemoDataSeederTest {

    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired TicketTypeRepository ticketTypes;
    @Autowired AnalyticsService analytics;

    @Test
    void seedsARealisticDataSet() {
        var organizer = users.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
        var caller = new AppUserPrincipal(organizer.getId(), organizer.getEmail(), organizer.getRole());

        var overview = analytics.overview(caller);

        assertThat(overview.totalEvents()).isEqualTo(4);
        assertThat(overview.publishedEvents()).isEqualTo(3);
        assertThat(overview.confirmedRegistrations()).isGreaterThan(200);
        assertThat(overview.refunded()).isPositive();
        assertThat(events.findAll()).anyMatch(e -> e.getStatus() == EventStatus.DRAFT);
        // inventory is consistent with bookings: nothing is oversold anywhere
        assertThat(ticketTypes.findAll()).allMatch(t -> t.getSold() >= 0 && t.getSold() <= t.getQuantity());
    }
}
