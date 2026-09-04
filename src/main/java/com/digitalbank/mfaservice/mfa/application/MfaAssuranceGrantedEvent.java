package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.domain.TransferChallengeBinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.Instant;

public record MfaAssuranceGrantedEvent(
        String eventId,
        String eventType,
        String schemaVersion,
        String producer,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String causationId,
        String transactionId,
        String reservationRequestId,
        String transferId,
        String customerId,
        String decisionId,
        String decisionRequestId,
        String challengeId,
        String assuranceType,
        String challengeType,
        Instant grantedAt,
        Instant expiresAt,
        String policyVersion,
        String sourceAccountId,
        String destinationAccountId,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal amount,
        String currency) {

    public static final String EVENT_TYPE = "MfaAssuranceGranted.v1";
    public static final String TOPIC = "mfa.assurance.granted.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String ASSURANCE_TYPE = "MFA";
    public static final String CHALLENGE_TYPE = "TOTP";

    public String toJson(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize MFA assurance event", exception);
        }
    }

    public static MfaAssuranceGrantedEvent from(
            String eventId,
            String producer,
            Instant occurredAt,
            Instant expiresAt,
            String challengeId,
            TransferChallengeBinding binding) {
        return new MfaAssuranceGrantedEvent(
                eventId,
                EVENT_TYPE,
                SCHEMA_VERSION,
                producer,
                occurredAt,
                binding.transferId(),
                binding.correlationId(),
                challengeId,
                binding.transferId(),
                binding.reservationRequestId(),
                binding.transferId(),
                binding.customerId(),
                binding.decisionId(),
                binding.decisionRequestId(),
                challengeId,
                ASSURANCE_TYPE,
                CHALLENGE_TYPE,
                occurredAt,
                expiresAt,
                binding.policyVersion(),
                binding.sourceAccountId(),
                binding.destinationAccountId(),
                binding.amount(),
                binding.currency());
    }

    public static MfaAssuranceGrantedEvent fixture(String eventId) {
        var occurredAt = Instant.parse("2026-09-04T10:15:30Z");
        return new MfaAssuranceGrantedEvent(
                eventId,
                EVENT_TYPE,
                SCHEMA_VERSION,
                "mfa-service",
                occurredAt,
                "transfer-stable",
                "correlation-stable",
                "challenge-stable",
                "transfer-stable",
                "reservation-stable",
                "transfer-stable",
                "customer-stable",
                "decision-stable",
                "decision-request-stable",
                "challenge-stable",
                ASSURANCE_TYPE,
                CHALLENGE_TYPE,
                occurredAt,
                occurredAt.plusSeconds(300),
                "policy-1",
                "account-source",
                "account-destination",
                new BigDecimal("10.0000"),
                "USD");
    }
}
