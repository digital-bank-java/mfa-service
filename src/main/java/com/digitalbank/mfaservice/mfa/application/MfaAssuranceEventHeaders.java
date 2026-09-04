package com.digitalbank.mfaservice.mfa.application;

import java.time.Instant;

public record MfaAssuranceEventHeaders(
        String eventId,
        String correlationId,
        String causationId,
        String producer,
        String schemaVersion,
        Instant occurredAt) {

    public static MfaAssuranceEventHeaders from(MfaAssuranceGrantedEvent event) {
        return new MfaAssuranceEventHeaders(
                event.eventId(),
                event.correlationId(),
                event.causationId(),
                event.producer(),
                event.schemaVersion(),
                event.occurredAt());
    }
}
