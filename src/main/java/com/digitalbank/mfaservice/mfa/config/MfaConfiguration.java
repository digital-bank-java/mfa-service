package com.digitalbank.mfaservice.mfa.config;

import com.digitalbank.mfaservice.mfa.adapter.persistence.PostgresChallengeStore;
import com.digitalbank.mfaservice.mfa.adapter.persistence.PostgresEnrollmentStore;
import com.digitalbank.mfaservice.mfa.adapter.persistence.TotpSecretProtector;
import com.digitalbank.mfaservice.mfa.adapter.random.SecureMfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.adapter.totp.SamStevensTotpProvider;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceOutboxStore;
import com.digitalbank.mfaservice.mfa.application.MfaChallengeService;
import com.digitalbank.mfaservice.mfa.application.TotpEnrollmentService;
import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.MfaTransactionRunner;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    TotpSecretProtector totpSecretProtector(MfaSecretProperties properties) {
        return new TotpSecretProtector(properties.getEncryptionKey());
    }

    @Bean
    EnrollmentStore enrollmentStore(JdbcTemplate jdbcTemplate, TotpSecretProtector secretProtector) {
        return new PostgresEnrollmentStore(jdbcTemplate, secretProtector);
    }

    @Bean
    ChallengeStore challengeStore(JdbcTemplate jdbcTemplate) {
        return new PostgresChallengeStore(jdbcTemplate);
    }

    @Bean
    MfaTransactionRunner mfaTransactionRunner(PlatformTransactionManager transactionManager) {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        return new MfaTransactionRunner() {
            @Override
            public <T> T execute(Supplier<T> action) {
                return Objects.requireNonNull(transactionTemplate.execute(status -> action.get()));
            }
        };
    }

    @Bean
    TotpEnrollmentService totpEnrollmentService(
            EnrollmentStore enrollmentStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock mfaClock,
            MfaTransactionRunner transactionRunner) {
        return new TotpEnrollmentService(
                enrollmentStore, totpProvider, identifierGenerator, mfaClock, transactionRunner);
    }

    @Bean
    MfaChallengeService mfaChallengeService(
            EnrollmentStore enrollmentStore,
            ChallengeStore challengeStore,
            TotpProvider totpProvider,
            MfaIdentifierGenerator identifierGenerator,
            Clock mfaClock,
            MfaProperties properties,
            MfaTransactionRunner transactionRunner,
            MfaAssuranceOutboxStore assuranceOutbox) {
        return new MfaChallengeService(
                enrollmentStore,
                challengeStore,
                totpProvider,
                identifierGenerator,
                mfaClock,
                properties.getTtl(),
                properties.getMaxAttempts(),
                transactionRunner,
                assuranceOutbox);
    }
}
