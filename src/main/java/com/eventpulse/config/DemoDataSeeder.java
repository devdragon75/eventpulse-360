package com.eventpulse.config;

import com.eventpulse.domain.Role;
import com.eventpulse.domain.User;
import com.eventpulse.dto.EventRequest;
import com.eventpulse.dto.RegistrationRequest;
import com.eventpulse.dto.RegistrationResponse;
import com.eventpulse.dto.TicketTypeRequest;
import com.eventpulse.dto.TicketTypeResponse;
import com.eventpulse.exception.SoldOutException;
import com.eventpulse.repository.UserRepository;
import com.eventpulse.security.AppUserPrincipal;
import com.eventpulse.service.EventService;
import com.eventpulse.service.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loads sample data for demos: {@code SPRING_PROFILES_ACTIVE=demo}. Goes through the real services, so all
 * business rules apply; registration timestamps are then back-dated so the trend charts have a shape.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@eventpulse.local";
    public static final String DEMO_PASSWORD = "demo12345";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[] FIRST = {"Ada", "Alan", "Grace", "Linus", "Margaret", "Dennis", "Barbara", "Ken",
            "Radia", "Tim", "Hedy", "Edsger", "Katherine", "Donald", "Frances", "Guido", "Sophie", "Vint"};
    private static final String[] LAST = {"Lovelace", "Turing", "Hopper", "Torvalds", "Hamilton", "Ritchie", "Liskov",
            "Thompson", "Perlman", "Berners", "Lamarr", "Dijkstra", "Johnson", "Knuth", "Allen", "Rossum", "Wilson", "Cerf"};

    private record Tier(String name, String price, int quantity, int weight) {
    }

    private record Spec(String title, String description, String venue, int capacity, int startsInDays,
                        int durationDays, boolean publish, int bookings, List<Tier> tiers) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec("Annual Tech Summit", "Two days of talks on cloud, data and AI for engineering teams.",
                    "Convention Center, Hall A", 500, 30, 2, true, 260,
                    List.of(new Tier("General", "120.00", 300, 6), new Tier("VIP", "350.00", 100, 2),
                            new Tier("Student", "40.00", 100, 3))),
            new Spec("Data Engineering Workshop", "Hands-on Spark, Kafka and lakehouse workshop. Small group.",
                    "Innovation Lab, Floor 3", 60, 10, 1, true, 70,
                    List.of(new Tier("Early Bird", "55.00", 20, 1), new Tier("Standard", "80.00", 40, 1))),
            new Spec("Product Leaders Forum", "Roadmaps, metrics and leadership for product managers.",
                    "Grand Hotel Ballroom", 200, 45, 1, true, 75,
                    List.of(new Tier("General", "200.00", 150, 6), new Tier("Sponsor", "900.00", 10, 1))),
            new Spec("Startup Pitch Night", "Draft event: ticket types are set up but it is not published yet.",
                    "Riverside Loft", 150, 60, 1, false, 0, List.of(new Tier("Free entry", "0.00", 150, 1))));

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final EventService events;
    private final RegistrationService registrations;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(UserRepository users, PasswordEncoder encoder, EventService events,
                          RegistrationService registrations, JdbcTemplate jdbc) {
        this.users = users;
        this.encoder = encoder;
        this.events = events;
        this.registrations = registrations;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(DEMO_EMAIL)) {
            return;
        }
        Random random = new Random(42);
        User organizer = users.save(new User("Demo Organizer", DEMO_EMAIL, encoder.encode(DEMO_PASSWORD), Role.ORGANIZER));
        AppUserPrincipal caller = new AppUserPrincipal(organizer.getId(), organizer.getEmail(), Role.ORGANIZER);

        int attendeeCounter = 0;
        int bookings = 0;
        int cancellations = 0;
        for (Spec spec : SPECS) {
            Instant start = Instant.now().plus(Duration.ofDays(spec.startsInDays()));
            Long eventId = events.create(new EventRequest(spec.title(), spec.description(), spec.venue(),
                    spec.capacity(), start, start.plus(Duration.ofDays(spec.durationDays()))), caller).id();

            List<TicketTypeResponse> tickets = new ArrayList<>();
            for (Tier tier : spec.tiers()) {
                tickets.add(events.addTicketType(eventId,
                        new TicketTypeRequest(tier.name(), new BigDecimal(tier.price()), tier.quantity()), caller));
            }
            if (spec.publish()) {
                events.publish(eventId, caller);
            }

            for (int i = 0; i < spec.bookings(); i++) {
                TicketTypeResponse ticket = pickWeighted(spec.tiers(), tickets, random);
                String first = FIRST[random.nextInt(FIRST.length)];
                String last = LAST[random.nextInt(LAST.length)];
                String email = (first + "." + last + "." + (++attendeeCounter)).toLowerCase() + "@example.com";
                try {
                    RegistrationResponse reg = registrations.register(eventId,
                            new RegistrationRequest(ticket.id(), first + " " + last, email));
                    backdate(reg.id(), random);
                    bookings++;
                    if (random.nextInt(100) < 7) {
                        registrations.cancel(reg.id(), caller);
                        cancellations++;
                    }
                } catch (SoldOutException e) {
                    // This tier is full; the data set intentionally includes a sold-out event.
                }
            }
        }
        log.info("Demo data ready: {} events, {} bookings ({} cancelled). Log in as {} / {}",
                SPECS.size(), bookings, cancellations, DEMO_EMAIL, DEMO_PASSWORD);
    }

    private static TicketTypeResponse pickWeighted(List<Tier> tiers, List<TicketTypeResponse> tickets, Random random) {
        int total = tiers.stream().mapToInt(Tier::weight).sum();
        int roll = random.nextInt(total);
        for (int i = 0; i < tiers.size(); i++) {
            roll -= tiers.get(i).weight();
            if (roll < 0) {
                return tickets.get(i);
            }
        }
        return tickets.get(0);
    }

    /** Spreads bookings over the last two weeks, busier towards the present. */
    private void backdate(Long registrationId, Random random) {
        double skew = Math.pow(random.nextDouble(), 1.6);
        Instant at = Instant.now().minus(Duration.ofSeconds((long) (skew * 14 * 24 * 3600)));
        jdbc.update("update registrations set registered_at = ? where registration_id = ?",
                at.atOffset(ZoneOffset.UTC), registrationId);
    }
}
