package com.digitalbank.mfaservice.mfa.adapter.totp;

import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrDataFactory;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SamStevensTotpProvider implements TotpProvider {

    private static final String ISSUER = "Digital Bank";

    private final DefaultSecretGenerator secretGenerator;
    private final QrDataFactory qrDataFactory;
    private final Clock clock;

    public SamStevensTotpProvider() {
        this(Clock.systemUTC());
    }

    public SamStevensTotpProvider(Clock clock) {
        this.secretGenerator = new DefaultSecretGenerator();
        this.qrDataFactory = new QrDataFactory(HashingAlgorithm.SHA1, 6, 30);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    @Override
    public boolean verify(String secret, String code, Instant at) {
        if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        var verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), () -> Objects.requireNonNull(at, "at")
                .getEpochSecond());
        verifier.setAllowedTimePeriodDiscrepancy(0);
        return verifier.isValidCode(secret, code);
    }

    @Override
    public String provisioningUri(String secret, String subjectId) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId must not be blank");
        }
        return qrDataFactory
                .newBuilder()
                .label(subjectId)
                .secret(secret)
                .issuer(ISSUER)
                .build()
                .getUri();
    }

    public boolean verify(String secret, String code) {
        return verify(secret, code, clock.instant());
    }
}
