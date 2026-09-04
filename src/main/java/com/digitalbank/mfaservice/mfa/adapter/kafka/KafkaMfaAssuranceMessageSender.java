package com.digitalbank.mfaservice.mfa.adapter.kafka;

import com.digitalbank.mfaservice.mfa.application.MfaAssuranceEventHeaders;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceMessageSender;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

public final class KafkaMfaAssuranceMessageSender implements MfaAssuranceMessageSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaMfaAssuranceMessageSender(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, String key, String payload, MfaAssuranceEventHeaders headers) {
        try {
            var record = new ProducerRecord<String, String>(topic, key, payload);
            record.headers().add("event-id", headers.eventId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("correlation-id", headers.correlationId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("causation-id", headers.causationId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("producer", headers.producer().getBytes(StandardCharsets.UTF_8));
            record.headers().add("schema-version", headers.schemaVersion().getBytes(StandardCharsets.UTF_8));
            record.headers().add("occurred-at", headers.occurredAt().toString().getBytes(StandardCharsets.UTF_8));
            kafkaTemplate.send(record).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Kafka publish failed", exception.getCause());
        }
    }
}
