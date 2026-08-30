package com.digitalbank.mfaservice;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;

@TestConfiguration
class TestSecurityConfig {

    static final String ISSUER = "https://issuer.test.internal";
    static final String TEST_BEARER_TOKEN = "test-internal-jwt";
    static final String WRONG_ISSUER_BEARER_TOKEN = "test-wrong-issuer-jwt";
    static final String EXPIRED_BEARER_TOKEN = "test-expired-jwt";
    static final String INSUFFICIENT_SCOPE_BEARER_TOKEN = "test-insufficient-scope-jwt";
    static final String MALFORMED_BEARER_TOKEN = "test-malformed-jwt";

    @Bean
    JwtDecoder testJwtDecoder() {
        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER));
        return token -> {
            Instant now = Instant.now();
            if (MALFORMED_BEARER_TOKEN.equals(token) || "invalid-jwt".equals(token)) {
                throw new BadJwtException("Malformed token");
            }
            Jwt jwt =
                    switch (token) {
                        case TEST_BEARER_TOKEN ->
                            jwt(token, ISSUER, now.minusSeconds(60), now.plusSeconds(3600), "mfa.internal");
                        case WRONG_ISSUER_BEARER_TOKEN ->
                            jwt(
                                    token,
                                    "https://wrong-issuer.test.internal",
                                    now.minusSeconds(60),
                                    now.plusSeconds(3600),
                                    "mfa.internal");
                        case EXPIRED_BEARER_TOKEN ->
                            jwt(token, ISSUER, now.minusSeconds(3600), now.minusSeconds(60), "mfa.internal");
                        case INSUFFICIENT_SCOPE_BEARER_TOKEN ->
                            jwt(token, ISSUER, now.minusSeconds(60), now.plusSeconds(3600), "other.scope");
                        default -> throw new JwtException("Invalid token");
                    };
            OAuth2TokenValidatorResult result = validator.validate(jwt);
            if (result.hasErrors()) {
                throw new JwtValidationException("Token validation failed", result.getErrors());
            }
            return jwt;
        };
    }

    private static Jwt jwt(String token, String issuer, Instant issuedAt, Instant expiresAt, String scope) {
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("internal-client")
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", scope)
                .build();
    }
}
