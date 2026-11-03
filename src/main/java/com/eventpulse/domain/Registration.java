package com.eventpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registrations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reg_ticket_code", columnNames = "ticket_code"),
        // One live registration per attendee per event; see active_flag below.
        @UniqueConstraint(name = "uk_reg_one_active", columnNames = {"attendee_id", "event_id", "active_flag"})
})
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registration_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendee_id", nullable = false)
    private Attendee attendee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private TicketType ticketType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.CONFIRMED;

    @Column(name = "ticket_code", nullable = false, length = 36, updatable = false)
    private String ticketCode = UUID.randomUUID().toString();

    /** Price paid, snapshotted so later price changes never rewrite history. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * TRUE while CONFIRMED, NULL once CANCELLED. Unique indexes ignore NULLs, so the composite
     * unique key (attendee, event, active_flag) blocks a second live registration but still lets
     * someone cancel and book again.
     */
    @Column(name = "active_flag")
    private Boolean activeFlag = Boolean.TRUE;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Registration() {
    }

    public Registration(Attendee attendee, Event event, TicketType ticketType) {
        this.attendee = attendee;
        this.event = event;
        this.ticketType = ticketType;
        this.amount = ticketType.getPrice();
    }

    public void cancel() {
        this.status = RegistrationStatus.CANCELLED;
        this.activeFlag = null;
        this.cancelledAt = Instant.now();
    }

    public boolean isConfirmed() {
        return status == RegistrationStatus.CONFIRMED;
    }

    public Long getId() {
        return id;
    }

    public Attendee getAttendee() {
        return attendee;
    }

    public Event getEvent() {
        return event;
    }

    public TicketType getTicketType() {
        return ticketType;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Boolean getActiveFlag() {
        return activeFlag;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
