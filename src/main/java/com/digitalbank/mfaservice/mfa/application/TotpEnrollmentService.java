package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.MfaTransactionRunner;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentVerificationOutcome;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;

public final class TotpEnrollmentService {

    private final EnrollmentStore enrollmentStore;
    private final TotpProvider totpProvider;
    private final MfaIdentifierGenerator identifierGenerator;
    private final Clock clock;
    private final MfaTransactionRunner transactionRunner;

    public TotpEnrollmentService(
            EnrollmentStore enrollmentStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock clock) {
        this(enrollmentStore, totpProvider, identifierGenerator, clock, MfaTransactionRunner.direct());
    }

    public TotpEnrollmentService(
            EnrollmentStore enrollmentStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock clock,
            MfaTransactionRunner transactionRunner) {
        this.enrollmentStore = Objects.requireNonNull(enrollmentStore, "enrollmentStore");
        this.totpProvider = Objects.requireNonNull(totpProvider, "totpProvider");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
    }

    public EnrollmentResult enroll(String subjectId) {
        var enrollmentId = identifierGenerator.newEnrollmentId();
        var secret = totpProvider.generateSecret();
        var enrollment = Enrollment.pending(enrollmentId, subjectId, secret, clock.instant());
        enrollmentStore.save(enrollment);
        return new EnrollmentResult(
                EnrollmentOutcome.ENROLLED, enrollmentId, enrollment.status(), provisioningUri(secret));
    }

    public EnrollmentResult verify(EnrollmentId enrollmentId, String subjectId, String code) {
        return transactionRunner.execute(() -> {
            var enrollment = enrollmentStore.find(enrollmentId);
            if (enrollment.isEmpty() || !enrollment.orElseThrow().subjectId().equals(subjectId)) {
                return new EnrollmentResult(EnrollmentOutcome.NOT_FOUND, enrollmentId, null, null);
            }
            var record = enrollment.orElseThrow();
            var verificationOutcome = record.verifyAndActivate(totpProvider, code, clock.instant());
            if (verificationOutcome == EnrollmentVerificationOutcome.ACTIVATED) {
                enrollmentStore.save(record);
            }
            var applicationOutcome =
                    switch (verificationOutcome) {
                        case ACTIVATED -> EnrollmentOutcome.ACTIVATED;
                        case INVALID_CODE -> EnrollmentOutcome.INVALID_CODE;
                        case ALREADY_ACTIVE -> EnrollmentOutcome.ALREADY_ACTIVE;
                    };
            return new EnrollmentResult(applicationOutcome, record.id(), record.status(), null);
        });
    }

    private static String provisioningUri(String secret) {
        var issuer = URLEncoder.encode("Digital Bank", StandardCharsets.UTF_8);
        return "otpauth://totp/" + issuer + "?secret=" + secret + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=6&period=30";
    }
}
