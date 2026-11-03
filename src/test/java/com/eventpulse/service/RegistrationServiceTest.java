package com.eventpulse.service;

import com.eventpulse.domain.Attendee;
import com.eventpulse.domain.Event;
import com.eventpulse.domain.EventStatus;
import com.eventpulse.domain.PaymentStatus;
import com.eventpulse.domain.Role;
import com.eventpulse.domain.TicketType;
import com.eventpulse.domain.User;
import com.eventpulse.dto.RegistrationRequest;
import com.eventpulse.dto.RegistrationResponse;
import com.eventpulse.exception.BusinessRuleException;
import com.eventpulse.exception.DuplicateRegistrationException;
import com.eventpulse.exception.SoldOutException;
import com.eventpulse.messaging.RegistrationEvent;
import com.eventpulse.messaging.RegistrationEventType;
import com.eventpulse.repository.EventRepository;
import com.eventpulse.repository.PaymentRepository;
import com.eventpulse.repository.RegistrationRepository;
import com.eventpulse.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock TicketTypeRepository ticketTypes;
    @Mock RegistrationRepository registrations;
    @Mock PaymentRepository payments;
    @Mock EventRepository events;
    @Mock AttendeeService attendees;
    @Mock ApplicationEventPublisher publisher;

    RegistrationService service;
    Event event;
    TicketType ticket;
    Attendee attendee;
    RegistrationRequest request;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(ticketTypes, registrations, payments, events, attendees,
                new AccessPolicy(), publisher);

        User organizer = new User("Org", "org@example.com", "hash", Role.ORGANIZER);
        ReflectionTestUtils.setField(organizer, "id", 1L);
        event = new Event("Conf", "desc", "Hall", 100, Instant.now().plus(10, ChronoUnit.DAYS),
                Instant.now().plus(11, ChronoUnit.DAYS), organizer);
        ReflectionTestUtils.setField(event, "id", 10L);
        event.setStatus(EventStatus.PUBLISHED);

        ticket = new TicketType(event, "General", new BigDecimal("49.99"), 2);
        ReflectionTestUtils.setField(ticket, "id", 100L);

        attendee = new Attendee("Ada", "ada@example.com");
        ReflectionTestUtils.setField(attendee, "id", 7L);

        request = new RegistrationRequest(100L, "Ada", "ada@example.com");
        when(ticketTypes.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
    }

    @Test
    void booksSeatTakesPaymentAndEmitsEvent() {
        when(attendees.findOrCreate("Ada", "ada@example.com")).thenReturn(attendee);
        when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RegistrationResponse response = service.register(10L, request);

        assertThat(ticket.getSold()).isEqualTo(1);
        assertThat(response.amount()).isEqualByComparingTo("49.99");
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        ArgumentCaptor<RegistrationEvent> captor = ArgumentCaptor.forClass(RegistrationEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(RegistrationEventType.REGISTRATION_CONFIRMED);
    }

    @Test
    void rejectsWhenSoldOut() {
        when(attendees.findOrCreate(any(), any())).thenReturn(attendee);
        ReflectionTestUtils.setField(ticket, "sold", 2);

        assertThatThrownBy(() -> service.register(10L, request)).isInstanceOf(SoldOutException.class);

        verify(registrations, never()).saveAndFlush(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void rejectsDuplicateRegistration() {
        when(attendees.findOrCreate(any(), any())).thenReturn(attendee);
        when(registrations.existsByAttendeeIdAndEventIdAndActiveFlagTrue(7L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.register(10L, request)).isInstanceOf(DuplicateRegistrationException.class);

        assertThat(ticket.getSold()).isZero();
    }

    @Test
    void rejectsDraftEvent() {
        event.setStatus(EventStatus.DRAFT);

        assertThatThrownBy(() -> service.register(10L, request)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsTicketFromAnotherEvent() {
        assertThatThrownBy(() -> service.register(999L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong");
    }
}
