package com.digitalbank.mfaservice.mfa.application;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

public final class MfaAssurancePublisher {

    private final MfaAssuranceOutboxStore outboxStore;
    private final MfaAssuranceMessageSender messageSender;
    private final int batchSize;
    private final Supplier<Instant> clock;

    public MfaAssurancePublisher(
            MfaAssuranceOutboxStore outboxStore,
            MfaAssuranceMessageSender messageSender,
            int batchSize,
            Supplier<Instant> clock) {
        this.outboxStore = Objects.requireNonNull(outboxStore, "outboxStore");
        this.messageSender = Objects.requireNonNull(messageSender, "messageSender");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void publishPending() {
        var attemptedAt = clock.get();
        for (var entry : outboxStore.findPending(batchSize, attemptedAt)) {
            try {
                messageSender.send(entry.topic(), entry.eventId(), entry.payload(), entry.headers());
                outboxStore.markPublished(entry.eventId(), attemptedAt);
            } catch (RuntimeException exception) {
                outboxStore.recordFailure(entry.eventId(), attemptedAt, exception.getMessage());
            }
        }
    }
}
