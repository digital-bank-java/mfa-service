package com.digitalbank.mfaservice.mfa.config;

import com.digitalbank.mfaservice.mfa.adapter.kafka.KafkaMfaAssuranceMessageSender;
import com.digitalbank.mfaservice.mfa.adapter.persistence.PostgresMfaAssuranceOutboxStore;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceMessageSender;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceOutboxStore;
import com.digitalbank.mfaservice.mfa.application.MfaAssurancePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MfaAssuranceProperties.class)
public class MfaAssuranceConfiguration {

    @Bean
    MfaAssuranceOutboxStore mfaAssuranceOutboxStore(JdbcTemplate jdbcTemplate) {
        return new PostgresMfaAssuranceOutboxStore(jdbcTemplate, new ObjectMapper().findAndRegisterModules());
    }

    @Bean
    @ConditionalOnProperty(prefix = "mfa.assurance.publisher", name = "enabled", havingValue = "true")
    MfaAssuranceMessageSender mfaAssuranceMessageSender(KafkaTemplate<String, String> kafkaTemplate) {
        return new KafkaMfaAssuranceMessageSender(kafkaTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mfa.assurance.publisher", name = "enabled", havingValue = "true")
    MfaAssurancePollingPublisher mfaAssurancePollingPublisher(
            MfaAssuranceOutboxStore outboxStore,
            MfaAssuranceMessageSender messageSender,
            MfaAssuranceProperties properties,
            Clock mfaClock) {
        return new MfaAssurancePollingPublisher(
                new MfaAssurancePublisher(outboxStore, messageSender, properties.getBatchSize(), mfaClock::instant));
    }

    static final class MfaAssurancePollingPublisher {
        private final MfaAssurancePublisher publisher;

        MfaAssurancePollingPublisher(MfaAssurancePublisher publisher) {
            this.publisher = publisher;
        }

        @Scheduled(fixedDelayString = "${mfa.assurance.publisher.poll-interval-ms:5000}")
        void publishPending() {
            publisher.publishPending();
        }
    }
}
