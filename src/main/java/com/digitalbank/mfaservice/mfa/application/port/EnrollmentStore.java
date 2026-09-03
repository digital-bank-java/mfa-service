package com.digitalbank.mfaservice.mfa.application.port;

import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.util.Optional;

public interface EnrollmentStore {

    void save(Enrollment enrollment);

    Optional<Enrollment> find(EnrollmentId enrollmentId);
}
