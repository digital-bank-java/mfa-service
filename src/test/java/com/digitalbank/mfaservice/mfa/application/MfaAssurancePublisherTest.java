package com.digitalbank.mfaservice.mfa.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MfaAssurancePublisherTest {

    @Test
    void retriesUseThePersistedEventIdentityAndPayload() {
        var event = MfaAssuranceGrantedEvent.fixture("event-stable");
        var store = new InMemoryStore(event);
        var sender = new RetryingSender();
        var publisher = new MfaAssurancePublisher(store, sender, 10, () -> Instant.parse("2026-09-04T10:15:30Z"));

        publisher.publishPending();
        publisher.publishPending();

        assertThat(sender.messages())
                .containsExactly(
                        new PublishedMessage("event-stable", "payload-stable"),
                        new PublishedMessage("event-stable", "payload-stable"));
        assertThat(store.entry().published()).isTrue();
    }

    private record PublishedMessage(String key, String payload) {}

    private static final class RetryingSender implements MfaAssuranceMessageSender {
        private final List<PublishedMessage> messages = new ArrayList<>();

        @Override
        public void send(String topic, String key, String payload, MfaAssuranceEventHeaders headers) {
            messages.add(new PublishedMessage(key, payload));
            if (messages.size() == 1) {
                throw new RuntimeException("temporary broker failure");
            }
        }

        List<PublishedMessage> messages() {
            return messages;
        }
    }

    private static final class InMemoryStore implements MfaAssuranceOutboxStore {
        private final MfaAssuranceOutboxEntry entry;

        private InMemoryStore(MfaAssuranceGrantedEvent event) {
            this.entry = MfaAssuranceOutboxEntry.pending(
                    event.eventId(),
                    MfaAssuranceGrantedEvent.TOPIC,
                    event.aggregateId(),
                    "payload-stable",
                    event.occurredAt());
        }

        @Override
        public void append(MfaAssuranceGrantedEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MfaAssuranceOutboxEntry> findPending(int limit, Instant at) {
            return entry.published() ? List.of() : List.of(entry);
        }

        @Override
        public void markPublished(String eventId, Instant publishedAt) {
            entry.markPublished();
        }

        @Override
        public void recordFailure(String eventId, Instant attemptedAt, String error) {}

        MfaAssuranceOutboxEntry entry() {
            return entry;
        }
    }
}
