package com.eventpulse.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Forwards registration events to Kafka <em>after the DB transaction commits</em>, so consumers never see
 * an event for a booking that was rolled back. If Kafka is down the booking still succeeds and the failure
 * is logged; a transactional outbox is the next step if guaranteed delivery is required.
 */
@Component
@ConditionalOnProperty(name = "eventpulse.kafka.enabled", havingValue = "true")
public class RegistrationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RegistrationEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public RegistrationEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                      @Value("${eventpulse.kafka.registration-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegistrationEvent(RegistrationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            // Keyed by event id: all activity for one event lands on one partition, in order.
            kafkaTemplate.send(topic, String.valueOf(event.eventId()), payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish {} for registration {}", event.eventType(), event.registrationId(), ex);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Could not serialise {} for registration {}", event.eventType(), event.registrationId(), e);
        }
    }
}
