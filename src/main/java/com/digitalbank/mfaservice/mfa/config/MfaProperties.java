package com.digitalbank.mfaservice.mfa.config;

import com.digitalbank.mfaservice.mfa.domain.Challenge;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mfa.challenge")
public class MfaProperties {

    private Duration ttl = Duration.ofMinutes(5);
    private int maxAttempts = 5;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Challenge TTL must be positive");
        }
        if (ttl.compareTo(Challenge.MAX_TTL) > 0) {
            throw new IllegalArgumentException("Challenge TTL must not exceed 15 minutes");
        }
        this.ttl = ttl;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("Challenge attempts must be between 1 and 10");
        }
        this.maxAttempts = maxAttempts;
    }
}
