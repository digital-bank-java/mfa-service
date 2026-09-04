package com.digitalbank.mfaservice.mfa.adapter.memory;

import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryEnrollmentStore implements EnrollmentStore {

    private final ConcurrentHashMap<EnrollmentId, Enrollment> enrollments = new ConcurrentHashMap<>();

    @Override
    public void save(Enrollment enrollment) {
        var previous = enrollments.putIfAbsent(enrollment.id(), enrollment);
        if (previous != null && previous != enrollment) {
            throw new IllegalStateException("Enrollment id already exists");
        }
    }

    @Override
    public Optional<Enrollment> find(EnrollmentId enrollmentId) {
        return Optional.ofNullable(enrollments.get(enrollmentId));
    }
}
