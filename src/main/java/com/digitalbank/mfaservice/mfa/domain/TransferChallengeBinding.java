package com.digitalbank.mfaservice.mfa.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/** Immutable transfer intent bound to a step-up challenge. */
public record TransferChallengeBinding(
        String transferId,
        String reservationRequestId,
        String decisionId,
        String decisionRequestId,
        String customerId,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        String policyVersion,
        String correlationId) {

    public TransferChallengeBinding {
        transferId = required(transferId, "transferId");
        reservationRequestId = required(reservationRequestId, "reservationRequestId");
        decisionId = required(decisionId, "decisionId");
        decisionRequestId = required(decisionRequestId, "decisionRequestId");
        customerId = required(customerId, "customerId");
        sourceAccountId = required(sourceAccountId, "sourceAccountId");
        destinationAccountId = required(destinationAccountId, "destinationAccountId");
        amount = Objects.requireNonNull(amount, "amount").setScale(4, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = required(currency, "currency").toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code");
        }
        policyVersion = required(policyVersion, "policyVersion");
        correlationId = required(correlationId, "correlationId");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
