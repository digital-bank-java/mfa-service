package com.digitalbank.mfaservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.mfaservice.mfa.application.port.TotpProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {MfaServiceApplication.class, MfaApiIT.TestConfig.class, TestSecurityConfig.class})
class MfaApiIT {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @LocalServerPort
    private int port;

    @Test
    void enrollsActivatesCreatesChallengeAndVerifies() throws Exception {
        var enrollmentResponse = sendAuthorizedJson("POST", "/api/v1/mfa/enrollments", """
                {
                  "subjectId": "subject-1"
                }
                """);

        assertThat(enrollmentResponse.statusCode()).isEqualTo(201);
        assertContentType(enrollmentResponse, "application/json");
        assertThat(enrollmentResponse.headers().firstValue("location"))
                .hasValueSatisfying(location -> assertThat(location).startsWith("/api/v1/mfa/enrollments/"));
        var enrollment = objectMapper.readTree(enrollmentResponse.body());
        assertThat(enrollment.path("status").asText()).isEqualTo("ENROLLED");
        assertThat(enrollment.path("enrollmentStatus").asText()).isEqualTo("PENDING");
        assertThat(enrollment.has("provisioningUri")).isFalse();
        assertThat(enrollmentResponse.body()).doesNotContain("TEST-SECRET");
        var enrollmentId = enrollment.path("enrollmentId").asText();

        var activationResponse =
                sendAuthorizedJson("POST", "/api/v1/mfa/enrollments/" + enrollmentId + "/verifications", """
                {
                  "code": "123456"
                }
                """);

        assertThat(activationResponse.statusCode()).isEqualTo(200);
        assertContentType(activationResponse, "application/json");
        var activated = objectMapper.readTree(activationResponse.body());
        assertThat(activated.path("status").asText()).isEqualTo("ACTIVATED");
        assertThat(activated.path("enrollmentId").asText()).isEqualTo(enrollmentId);
        assertThat(activated.path("enrollmentStatus").asText()).isEqualTo("ACTIVE");
        assertThat(activated.has("provisioningUri")).isFalse();

        var challengeResponse = sendAuthorizedJson("POST", "/api/v1/mfa/challenges", """
                {
                  "enrollmentId": "%s"
                }
                """.formatted(enrollmentId));

        assertThat(challengeResponse.statusCode()).isEqualTo(201);
        assertContentType(challengeResponse, "application/json");
        assertThat(challengeResponse.headers().firstValue("location"))
                .hasValueSatisfying(location -> assertThat(location).startsWith("/api/v1/mfa/challenges/"));
        var challenge = objectMapper.readTree(challengeResponse.body());
        assertThat(challenge.path("status").asText()).isEqualTo("CREATED");
        assertThat(challenge.path("challengeStatus").asText()).isEqualTo("OPEN");
        assertThat(challenge.path("remainingAttempts").asInt()).isEqualTo(5);
        var challengeId = challenge.path("challengeId").asText();

        var verificationResponse =
                sendAuthorizedJson("POST", "/api/v1/mfa/challenges/" + challengeId + "/verifications", """
                {
                  "code": "123456"
                }
                """);

        assertThat(verificationResponse.statusCode()).isEqualTo(200);
        assertContentType(verificationResponse, "application/json");
        var verified = objectMapper.readTree(verificationResponse.body());
        assertThat(verified.path("status").asText()).isEqualTo("VERIFIED");
        assertThat(verified.path("challengeId").asText()).isEqualTo(challengeId);
        assertThat(verified.path("challengeStatus").asText()).isEqualTo("CONSUMED");
        assertThat(verified.path("remainingAttempts").asInt()).isEqualTo(5);
    }

    @Test
    void returnsProblemForInvalidChallengeCode() throws Exception {
        var enrollmentId = activateEnrollment();
        var challengeResponse = sendAuthorizedJson("POST", "/api/v1/mfa/challenges", """
                {
                  "enrollmentId": "%s"
                }
                """.formatted(enrollmentId));
        var challengeId = objectMapper
                .readTree(challengeResponse.body())
                .path("challengeId")
                .asText();

        var response = sendAuthorizedJson("POST", "/api/v1/mfa/challenges/" + challengeId + "/verifications", """
                {
                  "code": "000000"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(401);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText()).isEqualTo("urn:digital-bank:mfa:invalid-code");
        assertThat(problem.path("title").asText()).isEqualTo("Invalid MFA code");
        assertThat(problem.path("remainingAttempts").asInt()).isEqualTo(4);
    }

    @Test
    void bindsEnrollmentToAuthenticatedSubjectInsteadOfRequestSubject() throws Exception {
        var enrollmentResponse = sendAuthorizedJson("POST", "/api/v1/mfa/enrollments", """
                {
                  "subjectId": "subject-2"
                }
                """);
        var enrollmentId = objectMapper
                .readTree(enrollmentResponse.body())
                .path("enrollmentId")
                .asText();

        var foreignVerification = sendAuthorizedJson(
                "POST",
                "/api/v1/mfa/enrollments/" + enrollmentId + "/verifications",
                """
                {
                  "code": "123456"
                }
                """,
                TestSecurityConfig.FOREIGN_BEARER_TOKEN);

        assertResourceNotFound(foreignVerification);

        var ownerVerification =
                sendAuthorizedJson("POST", "/api/v1/mfa/enrollments/" + enrollmentId + "/verifications", """
                {
                  "code": "123456"
                }
                """);

        assertThat(ownerVerification.statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsForeignPrincipalFromEnrollmentAndChallengeOperations() throws Exception {
        var enrollmentId = activateEnrollment();
        var challengeResponse = sendAuthorizedJson("POST", "/api/v1/mfa/challenges", """
                {
                  "enrollmentId": "%s"
                }
                """.formatted(enrollmentId));
        var challengeId = objectMapper
                .readTree(challengeResponse.body())
                .path("challengeId")
                .asText();

        var foreignChallengeCreation = sendAuthorizedJson(
                "POST", "/api/v1/mfa/challenges", """
                {
                  "enrollmentId": "%s"
                }
                """.formatted(enrollmentId), TestSecurityConfig.FOREIGN_BEARER_TOKEN);
        assertResourceNotFound(foreignChallengeCreation);

        var foreignChallengeVerification = sendAuthorizedJson(
                "POST",
                "/api/v1/mfa/challenges/" + challengeId + "/verifications",
                """
                {
                  "code": "123456"
                }
                """,
                TestSecurityConfig.FOREIGN_BEARER_TOKEN);
        assertResourceNotFound(foreignChallengeVerification);
    }

    @Test
    void rejectsUnauthenticatedRequestsForAllMfaEndpoints() throws Exception {
        assertAuthenticationProblem(sendJson("POST", "/api/v1/mfa/enrollments", """
                        {
                          "subjectId": "subject-1"
                        }
                        """), "/api/v1/mfa/enrollments");
        assertAuthenticationProblem(
                sendJson("POST", "/api/v1/mfa/enrollments/enrollment-1/verifications", """
                        {
                          "code": "123456"
                        }
                        """),
                "/api/v1/mfa/enrollments/enrollment-1/verifications");
        assertAuthenticationProblem(sendJson("POST", "/api/v1/mfa/challenges", """
                        {
                          "enrollmentId": "enrollment-1"
                        }
                        """), "/api/v1/mfa/challenges");
        assertAuthenticationProblem(
                sendJson("POST", "/api/v1/mfa/challenges/challenge-1/verifications", """
                        {
                          "code": "123456"
                        }
                        """),
                "/api/v1/mfa/challenges/challenge-1/verifications");
    }

    @Test
    void rejectsMalformedBearerTokenAsProblemDetails() throws Exception {
        var response =
                sendJson("POST", "/api/v1/mfa/enrollments", """
                {
                  "subjectId": "subject-1"
                }
                """, "Bearer " + TestSecurityConfig.MALFORMED_BEARER_TOKEN);

        assertAuthenticationProblem(response, "/api/v1/mfa/enrollments");
    }

    @Test
    void rejectsExpiredBearerTokenAsProblemDetails() throws Exception {
        var response =
                sendJson("POST", "/api/v1/mfa/enrollments", """
                {
                  "subjectId": "subject-1"
                }
                """, "Bearer " + TestSecurityConfig.EXPIRED_BEARER_TOKEN);

        assertAuthenticationProblem(response, "/api/v1/mfa/enrollments");
    }

    @Test
    void rejectsWrongIssuerBearerTokenAsProblemDetails() throws Exception {
        var response = sendJson(
                "POST", "/api/v1/mfa/enrollments", """
                {
                  "subjectId": "subject-1"
                }
                """, "Bearer " + TestSecurityConfig.WRONG_ISSUER_BEARER_TOKEN);

        assertAuthenticationProblem(response, "/api/v1/mfa/enrollments");
    }

    @Test
    void rejectsInsufficientScopeAsProblemDetails() throws Exception {
        var response = sendJson(
                "POST", "/api/v1/mfa/enrollments", """
                {
                  "subjectId": "subject-1"
                }
                """, "Bearer " + TestSecurityConfig.INSUFFICIENT_SCOPE_BEARER_TOKEN);

        assertThat(response.statusCode()).isEqualTo(403);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText()).isEqualTo("urn:digital-bank:mfa:access-denied");
        assertThat(problem.path("title").asText()).isEqualTo("MFA access denied");
        assertThat(problem.path("instance").asText()).isEqualTo("/api/v1/mfa/enrollments");
        assertThat(problem.path("detail").asText()).doesNotContain("scope");
    }

    @Test
    void rejectsInvalidVerificationRequest() throws Exception {
        var response = sendAuthorizedJson("POST", "/api/v1/mfa/enrollments/enrollment-1/verifications", """
                {
                  "code": "12A"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/validation-error");
        assertThat(problem.path("title").asText()).isEqualTo("Invalid request");
        assertThat(problem.path("errors")).isNotEmpty();
    }

    @Test
    void rejectsMalformedEnrollmentRequest() throws Exception {
        var response = sendAuthorizedJson("POST", "/api/v1/mfa/enrollments", """
                {
                  "subjectId":
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/validation-error");
        assertThat(problem.path("title").asText()).isEqualTo("Invalid request");
    }

    @Test
    void publishesOpenApiContractForMfaRoutes() throws Exception {
        var response = send("GET", "/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertContentType(response, "application/json");
        var openApi = objectMapper.readTree(response.body());
        assertThat(openApi.path("info").path("title").asText())
                .isEqualTo("Digital Bank Multi-Factor Authentication Service API");
        assertThat(openApi.path("paths").has("/api/v1/mfa/enrollments")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/mfa/enrollments/{enrollmentId}/verifications"))
                .isTrue();
        assertThat(openApi.path("paths").has("/api/v1/mfa/challenges")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/mfa/challenges/{challengeId}/verifications"))
                .isTrue();
        assertThat(openApi.path("components")
                        .path("securitySchemes")
                        .path("bearer-jwt")
                        .path("scheme")
                        .asText())
                .isEqualTo("bearer");
        assertThat(openApi.path("paths")
                        .path("/api/v1/mfa/enrollments")
                        .path("post")
                        .path("responses")
                        .path("401")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("authentication-required")
                        .path("value")
                        .path("type")
                        .asText())
                .isEqualTo("urn:digital-bank:mfa:authentication-required");

        var enrollResponses = openApi.path("paths")
                .path("/api/v1/mfa/enrollments")
                .path("post")
                .path("responses");
        assertThat(enrollResponses.path("201").path("content").has("application/json"))
                .isTrue();
        assertThat(openApi.path("components")
                        .path("schemas")
                        .path("EnrollmentResponse")
                        .path("properties")
                        .has("provisioningUri"))
                .isFalse();
        var createEnrollmentSchema = openApi.path("components").path("schemas").path("CreateEnrollmentRequest");
        assertThat(createEnrollmentSchema
                        .path("properties")
                        .path("subjectId")
                        .path("deprecated")
                        .asBoolean())
                .isTrue();
        assertThat(createEnrollmentSchema.path("required").isArray()).isFalse();
        assertThat(openApi.path("paths")
                        .path("/api/v1/mfa/enrollments")
                        .path("post")
                        .path("security")
                        .isArray())
                .isTrue();
        assertThat(enrollResponses.path("400").path("content").has("application/problem+json"))
                .isTrue();

        var verifyEnrollmentResponses = openApi.path("paths")
                .path("/api/v1/mfa/enrollments/{enrollmentId}/verifications")
                .path("post")
                .path("responses");
        assertThat(verifyEnrollmentResponses.path("401").path("content").has("application/problem+json"))
                .isTrue();
        assertThat(verifyEnrollmentResponses.path("409").path("content").has("application/problem+json"))
                .isTrue();
        assertThat(verifyEnrollmentResponses
                        .path("400")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("validation-error")
                        .path("value")
                        .path("instance")
                        .asText())
                .isEqualTo("/api/v1/mfa/enrollments/enrollment-1/verifications");
        assertThat(verifyEnrollmentResponses
                        .path("400")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("validation-error")
                        .path("value")
                        .path("errors")
                        .get(0)
                        .path("message")
                        .asText())
                .isEqualTo("must contain exactly 6 digits");
        assertThat(verifyEnrollmentResponses
                        .path("401")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("invalid-code")
                        .path("value")
                        .has("remainingAttempts"))
                .isFalse();
        assertThat(verifyEnrollmentResponses
                        .path("401")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("authentication-required")
                        .path("value")
                        .path("instance")
                        .asText())
                .isEqualTo("/api/v1/mfa/enrollments/enrollment-1/verifications");
        assertThat(verifyEnrollmentResponses
                        .path("409")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("enrollment-already-active")
                        .path("value")
                        .path("type")
                        .asText())
                .isEqualTo("urn:digital-bank:mfa:enrollment-already-active");

        var verifyChallengeResponses = openApi.path("paths")
                .path("/api/v1/mfa/challenges/{challengeId}/verifications")
                .path("post")
                .path("responses");
        assertThat(verifyChallengeResponses.path("200").path("content").has("application/json"))
                .isTrue();
        assertThat(verifyChallengeResponses.path("401").path("content").has("application/problem+json"))
                .isTrue();
        assertThat(verifyChallengeResponses.path("404").path("content").has("application/problem+json"))
                .isTrue();
        assertThat(openApi.path("paths")
                        .path("/api/v1/mfa/challenges")
                        .path("post")
                        .path("responses")
                        .path("404")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("resource-not-found")
                        .path("value")
                        .path("instance")
                        .asText())
                .isEqualTo("/api/v1/mfa/challenges");
        assertThat(verifyChallengeResponses
                        .path("404")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("resource-not-found")
                        .path("value")
                        .path("instance")
                        .asText())
                .isEqualTo("/api/v1/mfa/challenges/challenge-404/verifications");
        assertThat(verifyChallengeResponses
                        .path("401")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("invalid-code")
                        .path("value")
                        .path("remainingAttempts")
                        .asInt())
                .isEqualTo(4);
        assertThat(verifyChallengeResponses
                        .path("400")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .path("validation-error")
                        .path("value")
                        .path("errors")
                        .get(0)
                        .path("message")
                        .asText())
                .isEqualTo("must contain exactly 6 digits");
    }

    private void assertAuthenticationProblem(HttpResponse<String> response, String expectedInstance) throws Exception {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("www-authenticate")).hasValue("Bearer");
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText()).isEqualTo("urn:digital-bank:mfa:authentication-required");
        assertThat(problem.path("title").asText()).isEqualTo("MFA authentication required");
        assertThat(problem.path("instance").asText()).isEqualTo(expectedInstance);
        assertThat(problem.path("detail").asText())
                .doesNotContain("issuer")
                .doesNotContain("expired")
                .doesNotContain("token");
    }

    private String activateEnrollment() throws Exception {
        var enrollmentResponse = sendAuthorizedJson("POST", "/api/v1/mfa/enrollments", """
                {
                  "subjectId": "subject-it"
                }
                """);
        var enrollmentId = objectMapper
                .readTree(enrollmentResponse.body())
                .path("enrollmentId")
                .asText();
        var activationResponse =
                sendAuthorizedJson("POST", "/api/v1/mfa/enrollments/" + enrollmentId + "/verifications", """
                {
                  "code": "123456"
                }
                """);
        assertThat(activationResponse.statusCode()).isEqualTo(200);
        return enrollmentId;
    }

    private static void assertContentType(HttpResponse<String> response, String expectedContentType) {
        assertThat(response.headers().firstValue("content-type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith(expectedContentType));
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendJson(String method, String path, String body) throws Exception {
        return sendJson(method, path, body, null);
    }

    private HttpResponse<String> sendAuthorizedJson(String method, String path, String body) throws Exception {
        return sendAuthorizedJson(method, path, body, TestSecurityConfig.TEST_BEARER_TOKEN);
    }

    private HttpResponse<String> sendAuthorizedJson(String method, String path, String body, String token)
            throws Exception {
        return sendJson(method, path, body, "Bearer " + token);
    }

    private void assertResourceNotFound(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(404);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText()).isEqualTo("urn:digital-bank:mfa:resource-not-found");
        assertThat(problem.path("title").asText()).isEqualTo("MFA resource not found");
    }

    private HttpResponse<String> sendJson(String method, String path, String body, String authorizationHeader)
            throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (authorizationHeader != null) {
            request.header("Authorization", authorizationHeader);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        TotpProvider testTotpProvider() {
            return new TotpProvider() {
                @Override
                public String generateSecret() {
                    return "TEST-SECRET";
                }

                @Override
                public boolean verify(String secret, String code, Instant at) {
                    return "TEST-SECRET".equals(secret) && "123456".equals(code);
                }
            };
        }
    }
}
