package com.eventpulse.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes registration events and "sends" the attendee notification (logged here; swap in SMTP/SES). */
@Component
@ConditionalOnProperty(name = "eventpulse.kafka.enabled", havingValue = "true")
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ObjectMapper objectMapper;

    public NotificationConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${eventpulse.kafka.registration-topic}")
    public void onMessage(String payload) throws JsonProcessingException {
        RegistrationEvent event = objectMapper.readValue(payload, RegistrationEvent.class);
        switch (event.eventType()) {
            case REGISTRATION_CONFIRMED -> log.info("[notify] Confirmation email -> {} for '{}' ({})",
                    event.attendeeEmail(), event.eventTitle(), event.ticketType());
            case REGISTRATION_CANCELLED -> log.info("[notify] Cancellation + refund notice -> {} for '{}'",
                    event.attendeeEmail(), event.eventTitle());
        }
    }
}
