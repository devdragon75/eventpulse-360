package com.eventpulse.controller;

import com.eventpulse.dto.AttendeeRequest;
import com.eventpulse.dto.AttendeeResponse;
import com.eventpulse.dto.PageResponse;
import com.eventpulse.service.AttendeeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendees")
@Tag(name = "Attendees")
public class AttendeeController {

    private final AttendeeService attendees;

    public AttendeeController(AttendeeService attendees) {
        this.attendees = attendees;
    }

    @GetMapping
    public PageResponse<AttendeeResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return attendees.list(pageable);
    }

    @GetMapping("/{id}")
    public AttendeeResponse get(@PathVariable Long id) {
        return attendees.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttendeeResponse create(@Valid @RequestBody AttendeeRequest request) {
        return attendees.create(request);
    }

    @PutMapping("/{id}")
    public AttendeeResponse update(@PathVariable Long id, @Valid @RequestBody AttendeeRequest request) {
        return attendees.update(id, request);
    }
}
