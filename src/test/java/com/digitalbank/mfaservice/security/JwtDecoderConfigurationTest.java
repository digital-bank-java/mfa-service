package com.digitalbank.mfaservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class JwtDecoderConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(JwtDecoderConfiguration.class);

    @Test
    void failsWhenJwkSetUriIsConfiguredWithoutIssuerUri() {
        contextRunner
                .withPropertyValues("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.test/jwks")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage(
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri must be configured"));
    }

    @Test
    void failsWhenNoIssuerUriIsConfigured() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(JwtDecoder.class));
    }

    @Test
    void allowsATestDecoderOverrideWithoutProductionIssuerConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(JwtDecoderConfiguration.class, TestDecoderConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDecoderConfiguration {

        @Bean
        JwtDecoder testDecoder() {
            return token -> {
                throw new UnsupportedOperationException("test decoder");
            };
        }
    }
}
