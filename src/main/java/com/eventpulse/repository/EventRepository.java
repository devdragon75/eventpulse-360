package com.eventpulse.repository;

import com.eventpulse.domain.Event;
import com.eventpulse.domain.EventStatus;
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

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = "organizer")
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "organizer")
    Page<Event> findByOrganizerId(Long organizerId, Pageable pageable);

    @EntityGraph(attributePaths = "organizer")
    Optional<Event> findWithOrganizerById(Long id);

    /** Serialises changes to an event's ticket allocation so total allocation never exceeds capacity. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    @Query("select count(e) from Event e where (:organizerId is null or e.organizer.id = :organizerId)")
    long countInScope(@Param("organizerId") Long organizerId);

    @Query("select count(e) from Event e where e.status = :status and (:organizerId is null or e.organizer.id = :organizerId)")
    long countByStatusInScope(@Param("status") EventStatus status, @Param("organizerId") Long organizerId);
}
