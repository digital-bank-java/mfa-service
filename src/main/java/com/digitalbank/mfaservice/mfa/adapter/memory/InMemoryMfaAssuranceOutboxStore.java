package com.digitalbank.mfaservice.mfa.adapter.memory;

import com.digitalbank.mfaservice.mfa.application.MfaAssuranceGrantedEvent;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceOutboxEntry;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceOutboxStore;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMfaAssuranceOutboxStore implements MfaAssuranceOutboxStore {

    private final ConcurrentHashMap<String, MfaAssuranceOutboxEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void append(MfaAssuranceGrantedEvent event) {
        entries.putIfAbsent(event.eventId(), MfaAssuranceOutboxEntry.pending(event));
    }

    @Override
    public List<MfaAssuranceOutboxEntry> findPending(int limit, Instant at) {
        return entries.values().stream()
                .filter(entry -> !entry.published())
                .limit(limit)
                .toList();
    }

    @Override
    public void markPublished(String eventId, Instant publishedAt) {
        var entry = entries.get(eventId);
        if (entry != null) {
            entry.markPublished();
        }
    }

    @Override
    public void recordFailure(String eventId, Instant attemptedAt, String error) {
        var entry = entries.get(eventId);
        if (entry != null) {
            entry.recordFailure();
        }
    }
}
