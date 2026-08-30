package com.digitalbank.mfaservice.mfa.application;

import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.time.Instant;

final class TestMfaFixtures {

    private TestMfaFixtures() {}

    static final class FakeTotpProvider implements TotpProvider {

        private final String secret;
        private boolean verificationResult;
        private int verificationCalls;
        private String lastVerifiedSecret;
        private Instant lastVerifiedInstant;

        FakeTotpProvider(String secret) {
            this.secret = secret;
        }

        @Override
        public String generateSecret() {
            return secret;
        }

        @Override
        public boolean verify(String secret, String code, Instant at) {
            verificationCalls++;
            lastVerifiedSecret = secret;
            lastVerifiedInstant = at;
            return verificationResult;
        }

        void setVerificationResult(boolean verificationResult) {
            this.verificationResult = verificationResult;
        }

        int verificationCalls() {
            return verificationCalls;
        }

        String lastVerifiedSecret() {
            return lastVerifiedSecret;
        }

        Instant lastVerifiedInstant() {
            return lastVerifiedInstant;
        }
    }

    static final class FixedIdentifierGenerator implements MfaIdentifierGenerator {

        private final EnrollmentId enrollmentId;

        FixedIdentifierGenerator(EnrollmentId enrollmentId) {
            this.enrollmentId = enrollmentId;
        }

        @Override
        public EnrollmentId newEnrollmentId() {
            return enrollmentId;
        }

        @Override
        public ChallengeId newChallengeId() {
            return new ChallengeId("challenge-1");
        }
    }
}
