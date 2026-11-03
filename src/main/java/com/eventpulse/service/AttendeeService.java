package com.eventpulse.service;

import com.eventpulse.domain.Attendee;
import com.eventpulse.dto.AttendeeRequest;
import com.eventpulse.dto.AttendeeResponse;
import com.eventpulse.dto.PageResponse;
import com.eventpulse.exception.BusinessRuleException;
import com.eventpulse.exception.ResourceNotFoundException;
import com.eventpulse.repository.AttendeeRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AttendeeService {

    private final AttendeeRepository attendees;

    public AttendeeService(AttendeeRepository attendees) {
        this.attendees = attendees;
    }

    public AttendeeResponse create(AttendeeRequest req) {
        String email = req.email().trim().toLowerCase();
        if (attendees.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("An attendee with this email already exists");
        }
        return AttendeeResponse.from(attendees.save(new Attendee(req.name().trim(), email)));
    }

    public AttendeeResponse update(Long id, AttendeeRequest req) {
        Attendee attendee = load(id);
        String email = req.email().trim().toLowerCase();
        attendees.findByEmail(email).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new BusinessRuleException("Another attendee already uses this email");
        });
        attendee.update(req.name().trim(), email);
        return AttendeeResponse.from(attendee);
    }

    @Transactional(readOnly = true)
    public AttendeeResponse get(Long id) {
        return AttendeeResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendeeResponse> list(Pageable pageable) {
        return PageResponse.from(attendees.findAll(pageable), AttendeeResponse::from);
    }

    /** Used by the booking flow: the same email always maps to the same attendee record. */
    Attendee findOrCreate(String name, String email) {
        String normalised = email.trim().toLowerCase();
        return attendees.findByEmail(normalised)
                .orElseGet(() -> attendees.saveAndFlush(new Attendee(name.trim(), normalised)));
    }

    private Attendee load(Long id) {
        return attendees.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attendee", id));
    }
}
