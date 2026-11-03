package com.eventpulse.service;

import com.eventpulse.domain.Attendee;
import com.eventpulse.domain.Event;
import com.eventpulse.domain.Payment;
import com.eventpulse.domain.PaymentStatus;
import com.eventpulse.domain.Registration;
import com.eventpulse.domain.TicketType;
import com.eventpulse.dto.PageResponse;
import com.eventpulse.dto.RegistrationRequest;
import com.eventpulse.dto.RegistrationResponse;
import com.eventpulse.exception.BusinessRuleException;
import com.eventpulse.exception.DuplicateRegistrationException;
import com.eventpulse.exception.ResourceNotFoundException;
import com.eventpulse.messaging.RegistrationEvent;
import com.eventpulse.messaging.RegistrationEventType;
import com.eventpulse.repository.EventRepository;
import com.eventpulse.repository.PaymentRepository;
import com.eventpulse.repository.RegistrationRepository;
import com.eventpulse.repository.TicketTypeRepository;
import com.eventpulse.security.AppUserPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RegistrationService {

    private final TicketTypeRepository ticketTypes;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    private final EventRepository events;
    private final AttendeeService attendees;
    private final AccessPolicy access;
    private final ApplicationEventPublisher publisher;

    public RegistrationService(TicketTypeRepository ticketTypes, RegistrationRepository registrations,
                               PaymentRepository payments, EventRepository events, AttendeeService attendees,
                               AccessPolicy access, ApplicationEventPublisher publisher) {
        this.ticketTypes = ticketTypes;
        this.registrations = registrations;
        this.payments = payments;
        this.events = events;
        this.attendees = attendees;
        this.access = access;
        this.publisher = publisher;
    }

    /**
     * Books one seat. Overbooking protection:
     * <ol>
     *   <li>the ticket-type row is read with {@code SELECT ... FOR UPDATE}, so concurrent bookings for the
     *       same inventory execute one at a time;</li>
     *   <li>{@link TicketType#reserveSeat()} checks {@code sold < quantity} while that lock is held;</li>
     *   <li>a DB CHECK constraint ({@code sold <= quantity}) backs it up;</li>
     *   <li>a unique key on (attendee, event, active_flag) stops one person holding two live seats.</li>
     * </ol>
     */
    @Transactional
    public RegistrationResponse register(Long eventId, RegistrationRequest req) {
        TicketType ticket = ticketTypes.findByIdForUpdate(req.ticketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type", req.ticketTypeId()));
        Event event = ticket.getEvent();
        if (!event.getId().equals(eventId)) {
            throw new BusinessRuleException("Ticket type does not belong to this event");
        }
        if (!event.isOpenForRegistration(Instant.now())) {
            throw new BusinessRuleException("Event is not open for registration");
        }

        Attendee attendee = attendees.findOrCreate(req.attendeeName(), req.attendeeEmail());
        if (registrations.existsByAttendeeIdAndEventIdAndActiveFlagTrue(attendee.getId(), eventId)) {
            throw new DuplicateRegistrationException();
        }

        ticket.reserveSeat(); // throws SoldOutException -> whole transaction rolls back

        Registration registration = new Registration(attendee, event, ticket);
        try {
            registrations.saveAndFlush(registration);
        } catch (DataIntegrityViolationException e) { // concurrent duplicate caught by the unique key
            throw new DuplicateRegistrationException();
        }
        Payment payment = payments.save(new Payment(registration, registration.getAmount()));

        publisher.publishEvent(RegistrationEvent.of(RegistrationEventType.REGISTRATION_CONFIRMED, registration));
        return RegistrationResponse.from(registration, payment.getStatus());
    }

    @Transactional
    public RegistrationResponse cancel(Long registrationId, AppUserPrincipal caller) {
        // Lock order is always registration -> ticket type; register() only ever locks the ticket type.
        Registration registration = registrations.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration", registrationId));
        access.assertCanManage(registration.getEvent(), caller);
        if (!registration.isConfirmed()) {
            throw new BusinessRuleException("Registration is already cancelled");
        }
        if (!registration.getEvent().getEndsAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("Registrations for a finished event cannot be cancelled");
        }

        TicketType ticket = ticketTypes.findByIdForUpdate(registration.getTicketType().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type", registration.getTicketType().getId()));
        ticket.releaseSeat();
        registration.cancel();
        Payment payment = payments.findByRegistrationId(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for registration", registrationId));
        payment.refund();

        publisher.publishEvent(RegistrationEvent.of(RegistrationEventType.REGISTRATION_CANCELLED, registration));
        return RegistrationResponse.from(registration, payment.getStatus());
    }

    @Transactional(readOnly = true)
    public RegistrationResponse get(Long id, AppUserPrincipal caller) {
        Registration registration = registrations.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registration", id));
        access.assertCanManage(registration.getEvent(), caller);
        return toResponse(registration);
    }

    /** Lookup by the ticket code printed on the attendee's ticket (the code itself is the secret). */
    @Transactional(readOnly = true)
    public RegistrationResponse getByTicketCode(String code) {
        Registration registration = registrations.findByTicketCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", code));
        return toResponse(registration);
    }

    @Transactional(readOnly = true)
    public PageResponse<RegistrationResponse> listForEvent(Long eventId, AppUserPrincipal caller, Pageable pageable) {
        Event event = events.findWithOrganizerById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        access.assertCanManage(event, caller);

        var page = registrations.findByEventId(eventId, pageable);
        // One query for all payment statuses on the page instead of one per row.
        Map<Long, PaymentStatus> statuses = payments
                .findByRegistrationIdIn(page.getContent().stream().map(Registration::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getRegistration().getId(), Payment::getStatus));
        return PageResponse.from(page, r -> RegistrationResponse.from(r, statuses.get(r.getId())));
    }

    private RegistrationResponse toResponse(Registration registration) {
        PaymentStatus status = payments.findByRegistrationId(registration.getId())
                .map(Payment::getStatus).orElse(null);
        return RegistrationResponse.from(registration, status);
    }
}
