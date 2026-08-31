package com.digitalbank.mfaservice.mfa.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MfaPropertiesTest {

    @Test
    void rejectsChallengeTtlBeyondTheSecurityBound() {
        var properties = new MfaProperties();

        assertThatThrownBy(() -> properties.setTtl(Duration.ofMinutes(16)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Challenge TTL must not exceed 15 minutes");
    }
}
