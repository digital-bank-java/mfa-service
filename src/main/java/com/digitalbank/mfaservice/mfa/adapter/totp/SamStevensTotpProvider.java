package com.digitalbank.mfaservice.mfa.adapter.totp;

import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SamStevensTotpProvider implements TotpProvider {

    private final DefaultSecretGenerator secretGenerator;
    private final Clock clock;

    public SamStevensTotpProvider() {
        this(Clock.systemUTC());
    }

    public SamStevensTotpProvider(Clock clock) {
        this.secretGenerator = new DefaultSecretGenerator();
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

    public boolean verify(String secret, String code) {
        return verify(secret, code, clock.instant());
    }
}
