package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.time.Clock;
import java.util.Objects;

public final class TotpEnrollmentService {

    private final EnrollmentStore enrollmentStore;
    private final TotpProvider totpProvider;
    private final MfaIdentifierGenerator identifierGenerator;
    private final Clock clock;

    public TotpEnrollmentService(
            EnrollmentStore enrollmentStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock clock) {
        this.enrollmentStore = Objects.requireNonNull(enrollmentStore, "enrollmentStore");
        this.totpProvider = Objects.requireNonNull(totpProvider, "totpProvider");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public EnrollmentResult enroll(String subjectId) {
        var enrollmentId = identifierGenerator.newEnrollmentId();
        var enrollment = Enrollment.pending(enrollmentId, subjectId, totpProvider.generateSecret(), clock.instant());
        enrollmentStore.save(enrollment);
        return new EnrollmentResult(EnrollmentOutcome.ENROLLED, enrollmentId, enrollment.status());
    }

    public EnrollmentResult verify(EnrollmentId enrollmentId, String subjectId, String code) {
        var enrollment = enrollmentStore.find(enrollmentId);
        if (enrollment.isEmpty() || !enrollment.orElseThrow().subjectId().equals(subjectId)) {
            return new EnrollmentResult(EnrollmentOutcome.NOT_FOUND, enrollmentId, null);
        }
        var record = enrollment.orElseThrow();
        if (record.status().equals(com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus.ACTIVE)) {
            return new EnrollmentResult(EnrollmentOutcome.ALREADY_ACTIVE, record.id(), record.status());
        }
        var activated = record.verifyAndActivate(totpProvider, code, clock.instant());
        return new EnrollmentResult(
                activated ? EnrollmentOutcome.ACTIVATED : EnrollmentOutcome.INVALID_CODE, record.id(), record.status());
    }
}
