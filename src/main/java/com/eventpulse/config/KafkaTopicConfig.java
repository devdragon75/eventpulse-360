package com.eventpulse.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "eventpulse.kafka.enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    NewTopic registrationTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().registrationTopic()).partitions(3).replicas(1).build();
    }
}
