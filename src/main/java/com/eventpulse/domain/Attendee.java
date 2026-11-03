package com.eventpulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "attendees", uniqueConstraints = @UniqueConstraint(name = "uk_attendees_email", columnNames = "email"))
public class Attendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendee_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Always stored lower-cased so the unique constraint is case-insensitive in practice. */
    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Attendee() {
    }

    public Attendee(String name, String email) {
        this.name = name;
        this.email = email.toLowerCase();
    }

    public void update(String name, String email) {
        this.name = name;
        this.email = email.toLowerCase();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
