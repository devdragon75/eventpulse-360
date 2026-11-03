package com.eventpulse.repository;

import com.eventpulse.domain.Registration;
import com.eventpulse.domain.RegistrationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    /** Locks the registration so two concurrent cancellations can't both release the same seat. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select r from Registration r where r.id = :id")
    Optional<Registration> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"attendee", "event", "ticketType"})
    Optional<Registration> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"attendee", "event", "ticketType"})
    Optional<Registration> findByTicketCode(String ticketCode);

    @EntityGraph(attributePaths = {"attendee", "event", "ticketType"})
    Page<Registration> findByEventId(Long eventId, Pageable pageable);

    boolean existsByAttendeeIdAndEventIdAndActiveFlagTrue(Long attendeeId, Long eventId);

    boolean existsByEventId(Long eventId);

    boolean existsByAttendeeId(Long attendeeId);

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    @Query("""
            select count(r) from Registration r
            where r.status = :status
              and (:organizerId is null or r.event.organizer.id = :organizerId)
            """)
    long countByStatusInScope(@Param("status") RegistrationStatus status, @Param("organizerId") Long organizerId);

    List<RegistrationTimestamp> findByEventIdAndRegisteredAtGreaterThanEqual(Long eventId, Instant from);
}
