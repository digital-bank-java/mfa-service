package com.digitalbank.mfaservice.mfa.adapter.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SamStevensTotpProviderTest {

    private static final String RFC_6238_BASE32_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void verifiesKnownRfc6238CodeAtInjectedInstant() {
        var provider = new SamStevensTotpProvider(Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));

        assertThat(provider.verify(RFC_6238_BASE32_SECRET, "287082", Instant.ofEpochSecond(59)))
                .isTrue();
        assertThat(provider.verify(RFC_6238_BASE32_SECRET, "000000", Instant.ofEpochSecond(59)))
                .isFalse();
    }

    @Test
    void generatesNonBlankBase32Secret() {
        var provider = new SamStevensTotpProvider(Clock.systemUTC());

        assertThat(provider.generateSecret()).matches("[A-Z2-7]+");
    }

    @Test
    void generatesAuthenticatorProvisioningUri() {
        var provider = new SamStevensTotpProvider(Clock.systemUTC());

        var uri = provider.provisioningUri(RFC_6238_BASE32_SECRET, "customer-123");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=" + RFC_6238_BASE32_SECRET);
        assertThat(uri).contains("issuer=");
        assertThat(uri).contains("customer-123");
    }
}
