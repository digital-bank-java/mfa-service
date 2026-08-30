package com.digitalbank.mfaservice;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@TestConfiguration
class TestSecurityConfig {

    static final String TEST_BEARER_TOKEN = "test-internal-jwt";

    @Bean
    JwtDecoder testJwtDecoder() {
        return token -> {
            if (!TEST_BEARER_TOKEN.equals(token)) {
                throw new JwtException("Invalid token");
            }
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("internal-client")
                    .issuedAt(Instant.parse("2026-08-31T00:00:00Z"))
                    .expiresAt(Instant.parse("2026-08-31T01:00:00Z"))
                    .claim("scope", "mfa.internal")
                    .build();
        };
    }
}
