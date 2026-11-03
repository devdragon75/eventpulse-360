package com.eventpulse.domain;

import com.eventpulse.exception.SoldOutException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * A sellable ticket product for an event (General, VIP, ...) with its own price and inventory.
 *
 * <p>This row is the contention point for bookings: {@code RegistrationService} loads it with a
 * pessimistic write lock, so the {@code sold < quantity} check and the increment are atomic.
 */
@Entity
@Table(name = "ticket_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_ticket_types_event_name", columnNames = {"event_id", "name"}))
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int sold;

    protected TicketType() {
    }

    public TicketType(Event event, String name, BigDecimal price, int quantity) {
        this.event = event;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public int remaining() {
        return quantity - sold;
    }

    /** Takes one seat. Must be called while holding the row lock. */
    public void reserveSeat() {
        if (sold >= quantity) {
            throw new SoldOutException(name);
        }
        sold++;
    }

    public void releaseSeat() {
        if (sold > 0) {
            sold--;
        }
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getSold() {
        return sold;
    }
}
