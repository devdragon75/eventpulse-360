package com.eventpulse.repository;

import com.eventpulse.domain.TicketType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    List<TicketType> findByEventIdOrderByPriceAsc(Long eventId);

    boolean existsByEventIdAndName(Long eventId, String name);

    @Query("select coalesce(sum(t.quantity), 0) from TicketType t where t.event.id = :eventId")
    long sumQuantityByEventId(@Param("eventId") Long eventId);

    /**
     * SELECT ... FOR UPDATE. Concurrent bookings of the same ticket type queue up on this row, so the
     * inventory check and the increment happen as one atomic step. The timeout turns a stuck lock into
     * a retryable error instead of a hung request.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findByIdForUpdate(@Param("id") Long id);
}
