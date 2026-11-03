package com.eventpulse.service;

import com.eventpulse.domain.Event;
import com.eventpulse.domain.EventStatus;
import com.eventpulse.domain.PaymentStatus;
import com.eventpulse.domain.RegistrationStatus;
import com.eventpulse.domain.TicketType;
import com.eventpulse.dto.analytics.DailyRegistrations;
import com.eventpulse.dto.analytics.EventAnalyticsResponse;
import com.eventpulse.dto.analytics.OverviewResponse;
import com.eventpulse.dto.analytics.TicketTypeRevenue;
import com.eventpulse.dto.analytics.TicketTypeSales;
import com.eventpulse.exception.ResourceNotFoundException;
import com.eventpulse.repository.EventRepository;
import com.eventpulse.repository.PaymentRepository;
import com.eventpulse.repository.RegistrationRepository;
import com.eventpulse.repository.RegistrationTimestamp;
import com.eventpulse.repository.TicketTypeRepository;
import com.eventpulse.security.AppUserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Operational (OLTP) reports straight from MySQL. Revenue semantics: a refunded payment flips to REFUNDED,
 * so {@code net = sum(PAID)}, {@code refunded = sum(REFUNDED)}, {@code gross = net + refunded}.
 * Heavier history-wide analysis belongs in the Spark Gold layer, not here.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final int TOP_EVENTS = 5;

    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    private final AccessPolicy access;

    public AnalyticsService(EventRepository events, TicketTypeRepository ticketTypes,
                            RegistrationRepository registrations, PaymentRepository payments, AccessPolicy access) {
        this.events = events;
        this.ticketTypes = ticketTypes;
        this.registrations = registrations;
        this.payments = payments;
        this.access = access;
    }

    public OverviewResponse overview(AppUserPrincipal caller) {
        Long scope = caller.organizerScope();
        BigDecimal net = payments.sumAmountInScope(scope, PaymentStatus.PAID);
        BigDecimal refunded = payments.sumAmountInScope(scope, PaymentStatus.REFUNDED);
        return new OverviewResponse(
                events.countInScope(scope),
                events.countByStatusInScope(EventStatus.PUBLISHED, scope),
                registrations.countByStatusInScope(RegistrationStatus.CONFIRMED, scope),
                net.add(refunded), refunded, net,
                payments.topEventsByRevenue(scope, PaymentStatus.PAID, PageRequest.of(0, TOP_EVENTS)));
    }

    public EventAnalyticsResponse eventAnalytics(Long eventId, AppUserPrincipal caller) {
        Event event = loadManaged(eventId, caller);
        List<TicketType> types = ticketTypes.findByEventIdOrderByPriceAsc(eventId);
        Map<Long, BigDecimal> revenueByType = payments.revenueByTicketType(eventId, PaymentStatus.PAID).stream()
                .collect(Collectors.toMap(TicketTypeRevenue::ticketTypeId, TicketTypeRevenue::revenue));

        List<TicketTypeSales> breakdown = types.stream()
                .map(t -> new TicketTypeSales(t.getId(), t.getName(), t.getPrice(), t.getQuantity(), t.getSold(),
                        revenueByType.getOrDefault(t.getId(), BigDecimal.ZERO)))
                .toList();

        int allocated = types.stream().mapToInt(TicketType::getQuantity).sum();
        int sold = types.stream().mapToInt(TicketType::getSold).sum();
        BigDecimal net = payments.sumAmountByEvent(eventId, PaymentStatus.PAID);
        BigDecimal refunded = payments.sumAmountByEvent(eventId, PaymentStatus.REFUNDED);

        return new EventAnalyticsResponse(event.getId(), event.getTitle(), event.getStatus(), event.getCapacity(),
                allocated, sold, percent(sold, event.getCapacity()), percent(sold, allocated),
                registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED),
                registrations.countByEventIdAndStatus(eventId, RegistrationStatus.CANCELLED),
                net.add(refunded), refunded, net, breakdown);
    }

    /** Registrations per UTC day for the last {@code days} days, zero-filled so charts have no gaps. */
    public List<DailyRegistrations> dailyRegistrations(Long eventId, int days, AppUserPrincipal caller) {
        loadManaged(eventId, caller);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate first = today.minusDays(days - 1L);

        List<RegistrationTimestamp> rows = registrations.findByEventIdAndRegisteredAtGreaterThanEqual(
                eventId, first.atStartOfDay().toInstant(ZoneOffset.UTC));

        Function<RegistrationTimestamp, LocalDate> day = r -> r.getRegisteredAt().atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, Long> created = rows.stream().collect(Collectors.groupingBy(day, Collectors.counting()));
        Map<LocalDate, Long> cancelled = rows.stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CANCELLED)
                .collect(Collectors.groupingBy(day, Collectors.counting()));

        return Stream.iterate(first, d -> !d.isAfter(today), d -> d.plusDays(1))
                .map(d -> new DailyRegistrations(d, created.getOrDefault(d, 0L), cancelled.getOrDefault(d, 0L)))
                .toList();
    }

    private Event loadManaged(Long eventId, AppUserPrincipal caller) {
        Event event = events.findWithOrganizerById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        access.assertCanManage(event, caller);
        return event;
    }

    private static double percent(int part, int whole) {
        if (whole == 0) {
            return 0;
        }
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
