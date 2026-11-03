package com.eventpulse.service;

import com.eventpulse.domain.Event;
import com.eventpulse.domain.EventStatus;
import com.eventpulse.domain.Role;
import com.eventpulse.domain.TicketType;
import com.eventpulse.domain.User;
import com.eventpulse.dto.RegistrationRequest;
import com.eventpulse.dto.RegistrationResponse;
import com.eventpulse.exception.DuplicateRegistrationException;
import com.eventpulse.exception.SoldOutException;
import com.eventpulse.repository.EventRepository;
import com.eventpulse.repository.RegistrationRepository;
import com.eventpulse.repository.TicketTypeRepository;
import com.eventpulse.repository.UserRepository;
import com.eventpulse.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hammers the booking flow from many threads against a real database to prove the locking works.
 * These are the tests to point at in an interview: they fail immediately if the lock is removed.
 */
@SpringBootTest
class RegistrationConcurrencyTest {

    @Autowired RegistrationService registrationService;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired TicketTypeRepository ticketTypes;
    @Autowired RegistrationRepository registrations;

    User organizer;
    Event event;

    @BeforeEach
    void setUp() {
        organizer = users.save(new User("Org", "org-" + UUID.randomUUID() + "@example.com", "hash", Role.ORGANIZER));
        event = new Event("Concurrency Conf " + UUID.randomUUID(), "desc", "Main Hall", 500,
                Instant.now().plus(10, ChronoUnit.DAYS), Instant.now().plus(11, ChronoUnit.DAYS), organizer);
        event.setStatus(EventStatus.PUBLISHED);
        event = events.save(event);
    }

    @Test
    void twentyFiveBuyersFightingForFiveSeatsYieldsExactlyFiveBookings() throws Exception {
        TicketType ticket = ticketTypes.save(new TicketType(event, "General", new BigDecimal("25.00"), 5));

        List<Callable<RegistrationResponse>> attempts = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String email = "buyer" + i + "-" + UUID.randomUUID() + "@example.com";
            attempts.add(() -> registrationService.register(event.getId(),
                    new RegistrationRequest(ticket.getId(), "Buyer", email)));
        }

        Outcome outcome = runConcurrently(attempts);

        assertThat(outcome.unexpected).as("unexpected failures").isEmpty();
        assertThat(outcome.successes).isEqualTo(5);
        assertThat(outcome.exceptions(SoldOutException.class)).isEqualTo(20);
        assertThat(ticketTypes.findById(ticket.getId()).orElseThrow().getSold()).isEqualTo(5);
        assertThat(registrations.countByEventIdAndStatus(event.getId(),
                com.eventpulse.domain.RegistrationStatus.CONFIRMED)).isEqualTo(5);
    }

    @Test
    void sameAttendeeSubmittingTenTimesAtOnceGetsExactlyOneSeat() throws Exception {
        TicketType ticket = ticketTypes.save(new TicketType(event, "General", new BigDecimal("25.00"), 50));
        String email = "double-click-" + UUID.randomUUID() + "@example.com";

        List<Callable<RegistrationResponse>> attempts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            attempts.add(() -> registrationService.register(event.getId(),
                    new RegistrationRequest(ticket.getId(), "Impatient Ivy", email)));
        }

        Outcome outcome = runConcurrently(attempts);

        assertThat(outcome.unexpected).isEmpty();
        assertThat(outcome.successes).isEqualTo(1);
        assertThat(outcome.exceptions(DuplicateRegistrationException.class)).isEqualTo(9);
        assertThat(ticketTypes.findById(ticket.getId()).orElseThrow().getSold()).isEqualTo(1);
    }

    @Test
    void cancellingFreesTheSeatAndAllowsRebooking() {
        TicketType ticket = ticketTypes.save(new TicketType(event, "General", new BigDecimal("25.00"), 1));
        var caller = new AppUserPrincipal(organizer.getId(), organizer.getEmail(), Role.ORGANIZER);
        var request = new RegistrationRequest(ticket.getId(), "Ada", "ada-" + UUID.randomUUID() + "@example.com");

        RegistrationResponse first = registrationService.register(event.getId(), request);
        registrationService.cancel(first.id(), caller);
        assertThat(ticketTypes.findById(ticket.getId()).orElseThrow().getSold()).isZero();

        RegistrationResponse second = registrationService.register(event.getId(), request);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(ticketTypes.findById(ticket.getId()).orElseThrow().getSold()).isEqualTo(1);
    }

    @Test
    void cancellingTwiceAtOnceReleasesTheSeatOnlyOnce() throws Exception {
        TicketType ticket = ticketTypes.save(new TicketType(event, "General", new BigDecimal("25.00"), 3));
        var caller = new AppUserPrincipal(organizer.getId(), organizer.getEmail(), Role.ORGANIZER);
        RegistrationResponse keep = registrationService.register(event.getId(),
                new RegistrationRequest(ticket.getId(), "Keeper", "keeper-" + UUID.randomUUID() + "@example.com"));
        RegistrationResponse drop = registrationService.register(event.getId(),
                new RegistrationRequest(ticket.getId(), "Dropper", "dropper-" + UUID.randomUUID() + "@example.com"));

        List<Callable<RegistrationResponse>> attempts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            attempts.add(() -> registrationService.cancel(drop.id(), caller));
        }
        Outcome outcome = runConcurrently(attempts);

        assertThat(outcome.successes).isEqualTo(1);
        assertThat(keep.id()).isNotNull();
        assertThat(ticketTypes.findById(ticket.getId()).orElseThrow().getSold()).isEqualTo(1);
    }

    // ---- harness ------------------------------------------------------------------------

    private record Outcome(int successes, List<Throwable> failures, List<Throwable> unexpected) {
        long exceptions(Class<? extends Throwable> type) {
            return failures.stream().filter(type::isInstance).count();
        }
    }

    /** Releases every task at the same instant so they genuinely contend. */
    private static <T> Outcome runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();

            int successes = 0;
            List<Throwable> failures = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    future.get();
                    successes++;
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            List<Throwable> unexpected = failures.stream()
                    .filter(t -> !(t instanceof SoldOutException)
                            && !(t instanceof DuplicateRegistrationException)
                            && !(t instanceof com.eventpulse.exception.BusinessRuleException))
                    .toList();
            return new Outcome(successes, failures, unexpected);
        } finally {
            pool.shutdownNow();
        }
    }
}
