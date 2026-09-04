package com.digitalbank.mfaservice.mfa.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryChallengeStore;
import com.digitalbank.mfaservice.mfa.adapter.memory.InMemoryEnrollmentStore;
import com.digitalbank.mfaservice.mfa.application.MfaChallengeService;
import com.digitalbank.mfaservice.mfa.application.TotpEnrollmentService;
import com.digitalbank.mfaservice.mfa.application.port.MfaIdentifierGenerator;
import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class TransferChallengeControllerTest {

    private static final EnrollmentId ENROLLMENT_ID = new EnrollmentId("enrollment-1");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("challenge-1");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var enrollmentStore = new InMemoryEnrollmentStore();
        var challengeStore = new InMemoryChallengeStore();
        var provider = new TestTotpProvider();
        var identifiers = new FixedIdentifierGenerator();
        var clock = Clock.fixed(Instant.parse("2026-09-04T10:15:30Z"), ZoneOffset.UTC);
        var enrollmentService = new TotpEnrollmentService(enrollmentStore, provider, identifiers, clock);
        var challengeService = new MfaChallengeService(
                enrollmentStore, challengeStore, provider, identifiers, clock, Duration.ofMinutes(5), 5);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MfaController(enrollmentService, challengeService))
                .setControllerAdvice(new MfaApiExceptionHandler())
                .setValidator(validator)
                .defaultRequest(post("/").principal(authentication("subject-1")))
                .build();
        enrollmentService.enroll("subject-1");
        enrollmentService.verify(ENROLLMENT_ID, "subject-1", "123456");
    }

    @Test
    void createsAndVerifiesTransferBoundChallenge() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/transfer-challenges")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengeId").value("challenge-1"))
                .andExpect(jsonPath("$.transferId").value("transfer-1"))
                .andExpect(jsonPath("$.decisionId").value("decision-1"))
                .andExpect(jsonPath("$.policyVersion").value("transfer-risk-policy-2026-09"));

        mockMvc.perform(post("/api/v1/mfa/transfer-challenges/challenge-1/verifications")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "transferId": "transfer-1",
                                  "decisionId": "decision-1",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.transferId").value("transfer-1"))
                .andExpect(jsonPath("$.decisionId").value("decision-1"));
    }

    @Test
    void rejectsVerificationForDifferentTransferBinding() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/transfer-challenges")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/mfa/transfer-challenges/challenge-1/verifications")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "transferId": "transfer-other",
                                  "decisionId": "decision-1",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:digital-bank:mfa:challenge-binding-mismatch"));
    }

    private static String requestBody() {
        return """
                {
                  "enrollmentId": "enrollment-1",
                  "transferId": "transfer-1",
                  "decisionId": "decision-1",
                  "sourceAccountId": "account-1",
                  "destinationAccountId": "account-2",
                  "amount": "1250.75",
                  "currency": "USD",
                  "policyVersion": "transfer-risk-policy-2026-09",
                  "correlationId": "transfer-1"
                }
                """;
    }

    private static final class FixedIdentifierGenerator implements MfaIdentifierGenerator {
        @Override
        public EnrollmentId newEnrollmentId() {
            return ENROLLMENT_ID;
        }

        @Override
        public ChallengeId newChallengeId() {
            return CHALLENGE_ID;
        }
    }

    private static final class TestTotpProvider implements TotpProvider {
        @Override
        public String generateSecret() {
            return "TEST-SECRET";
        }

        @Override
        public boolean verify(String secret, String code, Instant at) {
            return "TEST-SECRET".equals(secret) && "123456".equals(code);
        }
    }

    private static JwtAuthenticationToken authentication(String subject) {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_mfa.internal")));
    }
}
