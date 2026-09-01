package com.digitalbank.mfaservice.mfa.domain;

import java.time.Instant;
import java.util.Objects;

public final class Enrollment {

    private final EnrollmentId id;
    private final String subjectId;
    private final Instant createdAt;
    private final TotpCredential credential;
    private EnrollmentStatus status;

    private Enrollment(EnrollmentId id, String subjectId, Instant createdAt, TotpCredential credential) {
        this.id = Objects.requireNonNull(id, "id");
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("Subject id must not be blank");
        }
        this.subjectId = subjectId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.credential = Objects.requireNonNull(credential, "credential");
        this.status = EnrollmentStatus.PENDING;
    }

    public static Enrollment pending(EnrollmentId id, String subjectId, String secret, Instant createdAt) {
        return new Enrollment(id, subjectId, createdAt, TotpCredential.fromSecret(secret));
    }

    public EnrollmentId id() {
        return id;
    }

    public String subjectId() {
        return subjectId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized EnrollmentStatus status() {
        return status;
    }

    public synchronized boolean verifyAndActivate(TotpCodeVerifier verifier, String code, Instant at) {
        Objects.requireNonNull(verifier, "verifier");
        Objects.requireNonNull(at, "at");
        if (status != EnrollmentStatus.PENDING) {
            return false;
        }
        if (!credential.verify(verifier, code, at)) {
            return false;
        }
        status = EnrollmentStatus.ACTIVE;
        return true;
    }

    public synchronized boolean verifyActive(TotpCodeVerifier verifier, String code, Instant at) {
        Objects.requireNonNull(verifier, "verifier");
        Objects.requireNonNull(at, "at");
        return status == EnrollmentStatus.ACTIVE && credential.verify(verifier, code, at);
    }

    @Override
    public synchronized String toString() {
        return "Enrollment[id=%s, subjectId=%s, createdAt=%s, status=%s]".formatted(id, subjectId, createdAt, status);
    }
}
