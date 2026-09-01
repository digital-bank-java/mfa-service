package com.digitalbank.mfaservice.mfa.application.port;

import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;

public interface MfaIdentifierGenerator {

    EnrollmentId newEnrollmentId();

    ChallengeId newChallengeId();
}
