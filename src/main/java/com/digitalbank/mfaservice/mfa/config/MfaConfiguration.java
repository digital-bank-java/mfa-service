package com.digitalbank.mfaservice.mfa.config;

import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryChallengeStore;
import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryEnrollmentStore;
import com.digitalbank.mfaservice.mfa.adapter.random.SecureMfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.adapter.totp.SamStevensTotpProvider;
import com.digitalbank.mfaservice.mfa.application.MfaChallengeService;
import com.digitalbank.mfaservice.mfa.application.TotpEnrollmentService;
import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MfaConfiguration {

    @Bean
    Clock mfaClock() {
        return Clock.systemUTC();
    }

    @Bean
    MfaIdentifierGenerator mfaIdentifierGenerator() {
        return new SecureMfaIdentifierGenerator();
    }

    @Bean
    TotpProvider totpProvider(Clock mfaClock) {
        return new SamStevensTotpProvider(mfaClock);
    }

    @Bean
    EnrollmentStore enrollmentStore() {
        return new InMemoryEnrollmentStore();
    }

    @Bean
    ChallengeStore challengeStore() {
        return new InMemoryChallengeStore();
    }

    @Bean
    TotpEnrollmentService totpEnrollmentService(
            EnrollmentStore enrollmentStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock mfaClock) {
        return new TotpEnrollmentService(enrollmentStore, totpProvider, identifierGenerator, mfaClock);
    }

    @Bean
    MfaChallengeService mfaChallengeService(
            EnrollmentStore enrollmentStore,
            ChallengeStore challengeStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock mfaClock,
            MfaProperties properties) {
        return new MfaChallengeService(
                enrollmentStore,
                challengeStore,
                totpProvider,
                identifierGenerator,
                mfaClock,
                properties.getTtl(),
                properties.getMaxAttempts());
    }
}
