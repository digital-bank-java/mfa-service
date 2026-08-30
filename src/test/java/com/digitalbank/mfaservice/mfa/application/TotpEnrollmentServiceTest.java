package com.digitalbank.mfaservice.mfa.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryEnrollmentStore;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TotpEnrollmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");
    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final EnrollmentId ENROLLMENT_ID = new EnrollmentId("enrollment-1");

    private final TestMfaFixtures.FakeTotpProvider provider = new TestMfaFixtures.FakeTotpProvider(SECRET);
    private final InMemoryEnrollmentStore store = new InMemoryEnrollmentStore();
    private final TestMfaFixtures.FixedIdentifierGenerator identifiers =
            new TestMfaFixtures.FixedIdentifierGenerator(ENROLLMENT_ID);
    private TotpEnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new TotpEnrollmentService(store, provider, identifiers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void enrollmentCreatesPendingRecordWithoutDisclosingSecretInResultsOrToString() {
        EnrollmentResult result = service.enroll("subject-1");

        assertThat(result.status()).isEqualTo(EnrollmentOutcome.ENROLLED);
        assertThat(result.enrollmentId()).isEqualTo(ENROLLMENT_ID);
        assertThat(result.enrollmentStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(result.toString()).doesNotContain(SECRET);
        assertThat(store.find(ENROLLMENT_ID).orElseThrow().toString()).doesNotContain(SECRET);
    }

    @Test
    void validCodeActivatesPendingEnrollment() {
        service.enroll("subject-1");
        provider.setVerificationResult(true);

        EnrollmentResult result = service.verify(ENROLLMENT_ID, "subject-1", "123456");

        assertThat(result.status()).isEqualTo(EnrollmentOutcome.ACTIVATED);
        assertThat(result.enrollmentStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(store.find(ENROLLMENT_ID).orElseThrow().status()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThat(provider.lastVerifiedSecret()).isEqualTo(SECRET);
        assertThat(provider.lastVerifiedInstant()).isEqualTo(NOW);
    }

    @Test
    void invalidCodeLeavesEnrollmentPending() {
        service.enroll("subject-1");
        provider.setVerificationResult(false);

        EnrollmentResult result = service.verify(ENROLLMENT_ID, "subject-1", "000000");

        assertThat(result.status()).isEqualTo(EnrollmentOutcome.INVALID_CODE);
        assertThat(result.enrollmentStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(store.find(ENROLLMENT_ID).orElseThrow().status()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    void unknownEnrollmentFailsWithoutProviderVerification() {
        EnrollmentResult result = service.verify(new EnrollmentId("missing"), "subject-1", "123456");

        assertThat(result.status()).isEqualTo(EnrollmentOutcome.NOT_FOUND);
        assertThat(provider.verificationCalls()).isZero();
    }

    @Test
    void activeEnrollmentCannotBeActivatedAgain() {
        service.enroll("subject-1");
        provider.setVerificationResult(true);
        service.verify(ENROLLMENT_ID, "subject-1", "123456");

        EnrollmentResult result = service.verify(ENROLLMENT_ID, "subject-1", "123456");

        assertThat(result.status()).isEqualTo(EnrollmentOutcome.ALREADY_ACTIVE);
        assertThat(provider.verificationCalls()).isEqualTo(1);
    }

    @Test
    void foreignSubjectCannotVerifyEnrollment() {
        service.enroll("subject-1");

        EnrollmentResult result = service.verify(ENROLLMENT_ID, "subject-2", "123456");

        assertThat(result.status()).isEqualTo(EnrollmentOutcome.NOT_FOUND);
        assertThat(provider.verificationCalls()).isZero();
        assertThat(store.find(ENROLLMENT_ID).orElseThrow().status()).isEqualTo(EnrollmentStatus.PENDING);
    }
}
