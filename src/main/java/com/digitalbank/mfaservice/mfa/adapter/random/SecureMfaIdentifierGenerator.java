package com.digitalbank.mfaservice.mfa.adapter.random;

import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.security.SecureRandom;
import java.util.UUID;

public final class SecureMfaIdentifierGenerator implements MfaIdentifierGenerator {

    private final SecureRandom random;

    public SecureMfaIdentifierGenerator() {
        this(new SecureRandom());
    }

    SecureMfaIdentifierGenerator(SecureRandom random) {
        this.random = random;
    }

    @Override
    public EnrollmentId newEnrollmentId() {
        return new EnrollmentId(nextUuid().toString());
    }

    @Override
    public ChallengeId newChallengeId() {
        return new ChallengeId(nextUuid().toString());
    }

    private UUID nextUuid() {
        return new UUID(random.nextLong(), random.nextLong());
    }
}
