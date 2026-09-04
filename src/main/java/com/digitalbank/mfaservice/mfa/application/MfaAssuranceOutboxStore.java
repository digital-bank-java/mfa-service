package com.digitalbank.mfaservice.mfa.application;

import java.time.Instant;
import java.util.List;

public interface MfaAssuranceOutboxStore {

    void append(MfaAssuranceGrantedEvent event);

    List<MfaAssuranceOutboxEntry> findPending(int limit, Instant at);

    void markPublished(String eventId, Instant publishedAt);

    void recordFailure(String eventId, Instant attemptedAt, String error);

    static MfaAssuranceOutboxStore noop() {
        return new MfaAssuranceOutboxStore() {
            @Override
            public void append(MfaAssuranceGrantedEvent event) {}

            @Override
            public List<MfaAssuranceOutboxEntry> findPending(int limit, Instant at) {
                return List.of();
            }

            @Override
            public void markPublished(String eventId, Instant publishedAt) {}

            @Override
            public void recordFailure(String eventId, Instant attemptedAt, String error) {}
        };
    }
}
