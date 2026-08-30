package com.digitalbank.mfaservice.mfa.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

class MfaControllerTest {

    private static final EnrollmentId ENROLLMENT_ID = new EnrollmentId("enrollment-1");
    private static final ChallengeId CHALLENGE_ID = new ChallengeId("challenge-1");

    private TotpEnrollmentService enrollmentService;
    private MfaChallengeService challengeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var enrollmentStore = new InMemoryEnrollmentStore();
        var challengeStore = new InMemoryChallengeStore();
        var provider = new TestTotpProvider();
        var identifiers = new FixedIdentifierGenerator();
        var clock = Clock.fixed(Instant.parse("2026-08-30T10:15:30Z"), ZoneOffset.UTC);

        enrollmentService = new TotpEnrollmentService(enrollmentStore, provider, identifiers, clock);
        challengeService = new MfaChallengeService(
                enrollmentStore, challengeStore, provider, identifiers, clock, Duration.ofMinutes(5), 5);

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new MfaController(enrollmentService, challengeService))
                .setControllerAdvice(new MfaApiExceptionHandler())
                .setValidator(validator)
                .defaultRequest(get("/").principal(authentication("subject-1")))
                .build();
    }

    @Test
    void createsEnrollment() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/enrollments")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": "subject-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/mfa/enrollments/enrollment-1"))
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.enrollmentId").value("enrollment-1"))
                .andExpect(jsonPath("$.enrollmentStatus").value("PENDING"))
                .andExpect(jsonPath("$.provisioningUri").doesNotExist());
    }

    @Test
    void acceptsLegacySubjectIdWithoutUsingItForOwnership() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/enrollments")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId": "subject-2"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").value("enrollment-1"));
    }

    @Test
    void mapsEnrollmentVerificationConflict() throws Exception {
        enrollmentService.enroll("subject-1");
        enrollmentService.verify(ENROLLMENT_ID, "subject-1", "123456");

        mockMvc.perform(post("/api/v1/mfa/enrollments/enrollment-1/verifications")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(header().string(
                                "Content-Type", org.hamcrest.Matchers.startsWith(APPLICATION_PROBLEM_JSON.toString())))
                .andExpect(jsonPath("$.type").value("urn:digital-bank:mfa:enrollment-already-active"))
                .andExpect(jsonPath("$.title").value("MFA enrollment already active"));
    }

    @Test
    void omitsRemainingAttemptsForEnrollmentVerificationFailure() throws Exception {
        enrollmentService.enroll("subject-1");

        mockMvc.perform(post("/api/v1/mfa/enrollments/enrollment-1/verifications")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "000000"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:digital-bank:mfa:invalid-code"))
                .andExpect(jsonPath("$.remainingAttempts").doesNotExist());
    }

    @Test
    void mapsChallengeVerificationFailure() throws Exception {
        enrollmentService.enroll("subject-1");
        enrollmentService.verify(ENROLLMENT_ID, "subject-1", "123456");
        challengeService.create(ENROLLMENT_ID, "subject-1");

        mockMvc.perform(post("/api/v1/mfa/challenges/challenge-1/verifications")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "000000"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                                "Content-Type", org.hamcrest.Matchers.startsWith(APPLICATION_PROBLEM_JSON.toString())))
                .andExpect(jsonPath("$.type").value("urn:digital-bank:mfa:invalid-code"))
                .andExpect(jsonPath("$.title").value("Invalid MFA code"))
                .andExpect(jsonPath("$.remainingAttempts").value(4));
    }

    @Test
    void rejectsMalformedJsonRequestBodyAsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/mfa/enrollments")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectId":
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                                "Content-Type", org.hamcrest.Matchers.startsWith(APPLICATION_PROBLEM_JSON.toString())))
                .andExpect(jsonPath("$.type").value("https://digital-bank-java.local/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Invalid request"));
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
