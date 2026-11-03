package com.eventpulse.repository;

import com.eventpulse.domain.Payment;
import com.eventpulse.domain.PaymentStatus;
import com.eventpulse.dto.analytics.EventRevenue;
import com.eventpulse.dto.analytics.TicketTypeRevenue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRegistrationId(Long registrationId);

    List<Payment> findByRegistrationIdIn(Collection<Long> registrationIds);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status and p.registration.event.id = :eventId
            """)
    BigDecimal sumAmountByEvent(@Param("eventId") Long eventId, @Param("status") PaymentStatus status);

    /** {@code organizerId == null} means "all organizers" (admin view). */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status
              and (:organizerId is null or p.registration.event.organizer.id = :organizerId)
            """)
    BigDecimal sumAmountInScope(@Param("organizerId") Long organizerId, @Param("status") PaymentStatus status);

    @Query("""
            select new com.eventpulse.dto.analytics.EventRevenue(e.id, e.title, count(p), coalesce(sum(p.amount), 0))
            from Payment p join p.registration r join r.event e
            where p.status = :status
              and (:organizerId is null or e.organizer.id = :organizerId)
            group by e.id, e.title
            order by sum(p.amount) desc
            """)
    List<EventRevenue> topEventsByRevenue(@Param("organizerId") Long organizerId,
                                          @Param("status") PaymentStatus status, Pageable pageable);

    @Query("""
            select new com.eventpulse.dto.analytics.TicketTypeRevenue(r.ticketType.id, coalesce(sum(p.amount), 0))
            from Payment p join p.registration r
            where r.event.id = :eventId and p.status = :status
            group by r.ticketType.id
            """)
    List<TicketTypeRevenue> revenueByTicketType(@Param("eventId") Long eventId,
                                                @Param("status") PaymentStatus status);
}
