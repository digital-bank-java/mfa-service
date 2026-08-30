package com.digitalbank.mfaservice.mfa.domain;

import java.time.Instant;

public final class TotpCredential {

    private final String secret;

    private TotpCredential(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("TOTP secret must not be blank");
        }
        this.secret = secret;
    }

    static TotpCredential fromSecret(String secret) {
        return new TotpCredential(secret);
    }

    boolean verify(TotpCodeVerifier verifier, String code, Instant at) {
        return verifier.verify(secret, code, at);
    }

    @Override
    public String toString() {
        return "TotpCredential[secret=REDACTED]";
    }
}
