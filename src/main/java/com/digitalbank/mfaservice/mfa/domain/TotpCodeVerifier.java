package com.digitalbank.mfaservice.mfa.domain;

import java.time.Instant;

@FunctionalInterface
public interface TotpCodeVerifier {

    boolean verify(String secret, String code, Instant at);
}
