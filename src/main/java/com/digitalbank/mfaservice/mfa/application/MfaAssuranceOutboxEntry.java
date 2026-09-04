package com.digitalbank.mfaservice.mfa.application;

import java.time.Instant;
import java.util.Objects;

public final class MfaAssuranceOutboxEntry {

    private final String eventId;
    private final String topic;
    private final String aggregateId;
    private final String payload;
    private final Instant createdAt;
    private final MfaAssuranceEventHeaders headers;
    private int attempts;
    private Instant publishedAt;

    public MfaAssuranceOutboxEntry(
            String eventId,
            String topic,
            String aggregateId,
            String payload,
            Instant createdAt,
            int attempts,
            Instant publishedAt,
            MfaAssuranceEventHeaders headers) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.headers = Objects.requireNonNull(headers, "headers");
        this.attempts = attempts;
        this.publishedAt = publishedAt;
    }

    public static MfaAssuranceOutboxEntry pending(MfaAssuranceGrantedEvent event) {
        return pending(event, event.toJson(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()));
    }

    public static MfaAssuranceOutboxEntry pending(MfaAssuranceGrantedEvent event, String payload) {
        return new MfaAssuranceOutboxEntry(
                event.eventId(),
                MfaAssuranceGrantedEvent.TOPIC,
                event.aggregateId(),
                payload,
                event.occurredAt(),
                0,
                null,
                MfaAssuranceEventHeaders.from(event));
    }

    public static MfaAssuranceOutboxEntry pending(
            String eventId, String topic, String aggregateId, String payload, Instant createdAt) {
        return new MfaAssuranceOutboxEntry(
                eventId,
                topic,
                aggregateId,
                payload,
                createdAt,
                0,
                null,
                new MfaAssuranceEventHeaders(
                        eventId, "correlation-unknown", "causation-unknown", "mfa-service", "1.0.0", createdAt));
    }

    public String eventId() {
        return eventId;
    }

    public String topic() {
        return topic;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String payload() {
        return payload;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public MfaAssuranceEventHeaders headers() {
        return headers;
    }

    public int attempts() {
        return attempts;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public boolean published() {
        return publishedAt != null;
    }

    public void markPublished() {
        publishedAt = Instant.now();
    }

    public void recordFailure() {
        attempts++;
    }
}
