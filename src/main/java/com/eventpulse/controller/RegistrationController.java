package com.eventpulse.controller;

import com.eventpulse.dto.PageResponse;
import com.eventpulse.dto.RegistrationRequest;
import com.eventpulse.dto.RegistrationResponse;
import com.eventpulse.security.AppUserPrincipal;
import com.eventpulse.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Registrations")
public class RegistrationController {

    private final RegistrationService registrations;

    public RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    @Operation(summary = "Register an attendee for an event (public). 409 if sold out or already registered.")
    @PostMapping("/events/{eventId}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@PathVariable Long eventId, @Valid @RequestBody RegistrationRequest request) {
        return registrations.register(eventId, request);
    }

    @Operation(summary = "Registrations for an event (organizer/admin)")
    @GetMapping("/events/{eventId}/registrations")
    public PageResponse<RegistrationResponse> listForEvent(
            @PathVariable Long eventId, @AuthenticationPrincipal AppUserPrincipal caller,
            @PageableDefault(size = 20, sort = "registeredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return registrations.listForEvent(eventId, caller, pageable);
    }

    @GetMapping("/registrations/{id}")
    public RegistrationResponse get(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal caller) {
        return registrations.get(id, caller);
    }

    @Operation(summary = "Look up a registration by its ticket code (public)")
    @GetMapping("/registrations/ticket/{code}")
    public RegistrationResponse getByTicketCode(@PathVariable String code) {
        return registrations.getByTicketCode(code);
    }

    @Operation(summary = "Cancel a registration: releases the seat and refunds the payment")
    @PostMapping("/registrations/{id}/cancel")
    public RegistrationResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AppUserPrincipal caller) {
        return registrations.cancel(id, caller);
    }
}
