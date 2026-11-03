package com.eventpulse.service;

import com.eventpulse.domain.Event;
import com.eventpulse.domain.EventStatus;
import com.eventpulse.domain.TicketType;
import com.eventpulse.domain.User;
import com.eventpulse.dto.EventRequest;
import com.eventpulse.dto.EventResponse;
import com.eventpulse.dto.PageResponse;
import com.eventpulse.dto.TicketTypeRequest;
import com.eventpulse.dto.TicketTypeResponse;
import com.eventpulse.exception.BusinessRuleException;
import com.eventpulse.exception.ResourceNotFoundException;
import com.eventpulse.repository.EventRepository;
import com.eventpulse.repository.RegistrationRepository;
import com.eventpulse.repository.TicketTypeRepository;
import com.eventpulse.repository.UserRepository;
import com.eventpulse.security.AppUserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class EventService {

    private final EventRepository events;
    private final TicketTypeRepository ticketTypes;
    private final RegistrationRepository registrations;
    private final UserRepository users;
    private final AccessPolicy access;

    public EventService(EventRepository events, TicketTypeRepository ticketTypes,
                        RegistrationRepository registrations, UserRepository users, AccessPolicy access) {
        this.events = events;
        this.ticketTypes = ticketTypes;
        this.registrations = registrations;
        this.users = users;
        this.access = access;
    }

    public EventResponse create(EventRequest req, AppUserPrincipal caller) {
        validateDates(req);
        User organizer = users.findById(caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", caller.id()));
        Event event = new Event(req.title().trim(), req.description(), req.venue().trim(), req.capacity(),
                req.startsAt(), req.endsAt(), organizer);
        return EventResponse.from(events.save(event));
    }

    public EventResponse update(Long id, EventRequest req, AppUserPrincipal caller) {
        validateDates(req);
        Event event = loadManaged(id, caller);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("A cancelled event cannot be edited");
        }
        if (req.capacity() < ticketTypes.sumQuantityByEventId(id)) {
            throw new BusinessRuleException("Capacity cannot be lower than the tickets already allocated");
        }
        event.update(req.title().trim(), req.description(), req.venue().trim(), req.capacity(),
                req.startsAt(), req.endsAt());
        return EventResponse.from(event);
    }

    public EventResponse publish(Long id, AppUserPrincipal caller) {
        Event event = loadManaged(id, caller);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BusinessRuleException("Only draft events can be published");
        }
        if (ticketTypes.findByEventIdOrderByPriceAsc(id).isEmpty()) {
            throw new BusinessRuleException("Add at least one ticket type before publishing");
        }
        event.setStatus(EventStatus.PUBLISHED);
        return EventResponse.from(event);
    }

    public EventResponse cancel(Long id, AppUserPrincipal caller) {
        Event event = loadManaged(id, caller);
        event.setStatus(EventStatus.CANCELLED);
        return EventResponse.from(event);
    }

    public void delete(Long id, AppUserPrincipal caller) {
        Event event = loadManaged(id, caller);
        if (registrations.existsByEventId(id)) {
            throw new BusinessRuleException("Events with registrations cannot be deleted; cancel the event instead");
        }
        ticketTypes.deleteAll(ticketTypes.findByEventIdOrderByPriceAsc(id));
        events.delete(event);
    }

    /** Draft events are only visible to whoever may manage them. */
    @Transactional(readOnly = true)
    public EventResponse getVisible(Long id, AppUserPrincipal caller) {
        Event event = events.findWithOrganizerById(id).orElseThrow(() -> new ResourceNotFoundException("Event", id));
        if (event.getStatus() == EventStatus.DRAFT && !access.canManage(event, caller)) {
            throw new ResourceNotFoundException("Event", id);
        }
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listPublished(Pageable pageable) {
        return PageResponse.from(events.findByStatus(EventStatus.PUBLISHED, pageable), EventResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> listMine(AppUserPrincipal caller, Pageable pageable) {
        var page = caller.isAdmin() ? events.findAll(pageable) : events.findByOrganizerId(caller.id(), pageable);
        return PageResponse.from(page, EventResponse::from);
    }

    // ---- ticket types -------------------------------------------------------------------

    public TicketTypeResponse addTicketType(Long eventId, TicketTypeRequest req, AppUserPrincipal caller) {
        // Lock the event row so two concurrent additions can't jointly exceed capacity.
        Event event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        access.assertCanManage(event, caller);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot add tickets to a cancelled event");
        }
        String name = req.name().trim();
        if (ticketTypes.existsByEventIdAndName(eventId, name)) {
            throw new BusinessRuleException("A ticket type named '" + name + "' already exists for this event");
        }
        long allocated = ticketTypes.sumQuantityByEventId(eventId);
        if (allocated + req.quantity() > event.getCapacity()) {
            throw new BusinessRuleException("Allocating %d more tickets would exceed the event capacity of %d (already allocated: %d)"
                    .formatted(req.quantity(), event.getCapacity(), allocated));
        }
        return TicketTypeResponse.from(ticketTypes.save(new TicketType(event, name, req.price(), req.quantity())));
    }

    @Transactional(readOnly = true)
    public List<TicketTypeResponse> listTicketTypes(Long eventId, AppUserPrincipal caller) {
        getVisible(eventId, caller); // enforces draft visibility
        return ticketTypes.findByEventIdOrderByPriceAsc(eventId).stream().map(TicketTypeResponse::from).toList();
    }

    // ---- helpers ------------------------------------------------------------------------

    private Event loadManaged(Long id, AppUserPrincipal caller) {
        Event event = events.findWithOrganizerById(id).orElseThrow(() -> new ResourceNotFoundException("Event", id));
        access.assertCanManage(event, caller);
        return event;
    }

    private static void validateDates(EventRequest req) {
        if (!req.endsAt().isAfter(req.startsAt())) {
            throw new BusinessRuleException("Event end time must be after its start time");
        }
        if (!req.endsAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("Event must end in the future");
        }
    }
}
