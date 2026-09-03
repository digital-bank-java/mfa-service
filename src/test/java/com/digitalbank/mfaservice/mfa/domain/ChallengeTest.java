package com.digitalbank.mfaservice.mfa.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChallengeTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void rejectsAnExpiryBeyondTheSecurityBound() {
        assertThatThrownBy(() -> Challenge.open(
                        new ChallengeId("challenge-1"),
                        new EnrollmentId("enrollment-1"),
                        CREATED_AT,
                        CREATED_AT.plus(Challenge.MAX_TTL).plusNanos(1),
                        5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Challenge TTL must not exceed 15 minutes");
    }
}
