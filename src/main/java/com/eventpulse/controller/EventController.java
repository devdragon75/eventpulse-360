package com.eventpulse.controller;

import com.eventpulse.dto.EventRequest;
import com.eventpulse.dto.EventResponse;
import com.eventpulse.dto.PageResponse;
import com.eventpulse.dto.TicketTypeRequest;
import com.eventpulse.dto.TicketTypeResponse;
import com.eventpulse.security.AppUserPrincipal;
import com.eventpulse.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Events")
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @Operation(summary = "Browse published events (public)")
    @GetMapping("/events")
    public PageResponse<EventResponse> listPublished(
            @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return events.listPublished(pageable);
    }

    @Operation(summary = "Events I manage (all events for admins)")
    @GetMapping("/me/events")
    public PageResponse<EventResponse> listMine(
            @AuthenticationPrincipal AppUserPrincipal caller,
            @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return events.listMine(caller, pageable);
    }

    @Operation(summary = "Get an event (drafts are visible only to their organizer/admin)")
    @GetMapping("/events/{id}")
    public EventResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal caller) {
        return events.getVisible(id, caller);
    }

    @Operation(summary = "Create an event (starts as DRAFT)")
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventRequest request, @AuthenticationPrincipal AppUserPrincipal caller) {
        return events.create(request, caller);
    }

    @PutMapping("/events/{id}")
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request,
                                @AuthenticationPrincipal AppUserPrincipal caller) {
        return events.update(id, request, caller);
    }

    @Operation(summary = "Publish a draft event so attendees can register")
    @PostMapping("/events/{id}/publish")
    public EventResponse publish(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal caller) {
        return events.publish(id, caller);
    }

    @PostMapping("/events/{id}/cancel")
    public EventResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal caller) {
        return events.cancel(id, caller);
    }

    @Operation(summary = "Delete an event that has no registrations")
    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal caller) {
        events.delete(id, caller);
    }

    @Operation(summary = "List ticket types and live availability for an event (public)")
    @GetMapping("/events/{id}/ticket-types")
    public List<TicketTypeResponse> listTicketTypes(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal caller) {
        return events.listTicketTypes(id, caller);
    }

    @Operation(summary = "Add a ticket type; total allocation may not exceed event capacity")
    @PostMapping("/events/{id}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketTypeResponse addTicketType(@PathVariable Long id, @Valid @RequestBody TicketTypeRequest request,
                                            @AuthenticationPrincipal AppUserPrincipal caller) {
        return events.addTicketType(id, request, caller);
    }
}
