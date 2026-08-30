package com.digitalbank.mfaservice.mfa.adapter.in.web;

import com.digitalbank.mfaservice.mfa.application.ChallengeResult;
import com.digitalbank.mfaservice.mfa.application.EnrollmentResult;
import com.digitalbank.mfaservice.mfa.application.MfaChallengeService;
import com.digitalbank.mfaservice.mfa.application.TotpEnrollmentService;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "MFA")
@SecurityRequirement(name = "bearer-jwt")
class MfaController {

    private static final String VALIDATION_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Request validation failed",
              "instance": "/api/v1/mfa/enrollments",
              "errors": [
                {
                  "field": "subjectId",
                  "message": "must not be blank"
                }
              ]
            }
            """;

    private static final String ENROLLMENT_INVALID_CODE_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:invalid-code",
              "title": "Invalid MFA code",
              "status": 401,
              "detail": "The submitted MFA code was not accepted.",
              "instance": "/api/v1/mfa/enrollments/enrollment-1/verifications"
            }
            """;

    private static final String CHALLENGE_INVALID_CODE_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:invalid-code",
              "title": "Invalid MFA code",
              "status": 401,
              "detail": "The submitted MFA code was not accepted.",
              "instance": "/api/v1/mfa/challenges/challenge-1/verifications",
              "remainingAttempts": 4
            }
            """;

    private static final String RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:resource-not-found",
              "title": "MFA resource not found",
              "status": 404,
              "detail": "The requested MFA enrollment does not exist.",
              "instance": "/api/v1/mfa/enrollments/enrollment-404/verifications"
            }
            """;

    private static final String ENROLLMENT_CONFLICT_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:enrollment-not-active",
              "title": "MFA enrollment not active",
              "status": 409,
              "detail": "The MFA enrollment must be active before a challenge can be created.",
              "instance": "/api/v1/mfa/challenges"
            }
            """;

    private static final String ENROLLMENT_ALREADY_ACTIVE_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:enrollment-already-active",
              "title": "MFA enrollment already active",
              "status": 409,
              "detail": "The MFA enrollment is already active.",
              "instance": "/api/v1/mfa/enrollments/enrollment-1/verifications"
            }
            """;

    private final TotpEnrollmentService enrollmentService;
    private final MfaChallengeService challengeService;

    MfaController(TotpEnrollmentService enrollmentService, MfaChallengeService challengeService) {
        this.enrollmentService = enrollmentService;
        this.challengeService = challengeService;
    }

    @PostMapping("/api/v1/mfa/enrollments")
    @Operation(summary = "Create an MFA enrollment")
    @ApiResponse(
            responseCode = "201",
            description = "Enrollment created",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EnrollmentResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "validation-error", value = VALIDATION_PROBLEM_EXAMPLE)))
    ResponseEntity<EnrollmentResponse> createEnrollment(@Valid @RequestBody CreateEnrollmentRequest request) {
        var result = enrollmentService.enroll(request.subjectId());
        var response = EnrollmentResponse.from(result);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/mfa/enrollments/" + response.enrollmentId()))
                .body(response);
    }

    @PostMapping("/api/v1/mfa/enrollments/{enrollmentId}/verifications")
    @Operation(summary = "Verify an MFA enrollment")
    @ApiResponse(
            responseCode = "200",
            description = "Enrollment verified",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EnrollmentResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "validation-error", value = VALIDATION_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "401",
            description = "Invalid MFA code",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "invalid-code",
                                            value = ENROLLMENT_INVALID_CODE_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "Enrollment not found",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "resource-not-found",
                                            value = RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "409",
            description = "Enrollment already active",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "enrollment-already-active",
                                            value = ENROLLMENT_ALREADY_ACTIVE_PROBLEM_EXAMPLE)))
    ResponseEntity<EnrollmentResponse> verifyEnrollment(
            @PathVariable String enrollmentId, @Valid @RequestBody VerifyTotpCodeRequest request) {
        var result = enrollmentService.verify(new EnrollmentId(enrollmentId), request.code());
        return ResponseEntity.ok(mapEnrollmentVerificationResult(result));
    }

    @PostMapping("/api/v1/mfa/challenges")
    @Operation(summary = "Create an MFA challenge")
    @ApiResponse(
            responseCode = "201",
            description = "Challenge created",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ChallengeResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "validation-error", value = VALIDATION_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "Enrollment not found",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "resource-not-found",
                                            value = RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "409",
            description = "Enrollment not active",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "enrollment-not-active",
                                            value = ENROLLMENT_CONFLICT_PROBLEM_EXAMPLE)))
    ResponseEntity<ChallengeResponse> createChallenge(@Valid @RequestBody CreateChallengeRequest request) {
        var result = challengeService.create(new EnrollmentId(request.enrollmentId()));
        var response = mapChallengeCreationResult(result);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/mfa/challenges/" + response.challengeId()))
                .body(response);
    }

    @PostMapping("/api/v1/mfa/challenges/{challengeId}/verifications")
    @Operation(summary = "Verify an MFA challenge")
    @ApiResponse(
            responseCode = "200",
            description = "Challenge verified",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ChallengeResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(name = "validation-error", value = VALIDATION_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "401",
            description = "Verification failed",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "invalid-code",
                                            value = CHALLENGE_INVALID_CODE_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "Challenge not found",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "resource-not-found",
                                            value = RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE)))
    ResponseEntity<ChallengeResponse> verifyChallenge(
            @PathVariable String challengeId, @Valid @RequestBody VerifyTotpCodeRequest request) {
        var result = challengeService.verify(new ChallengeId(challengeId), request.code());
        return ResponseEntity.ok(mapChallengeVerificationResult(result));
    }

    private static EnrollmentResponse mapEnrollmentVerificationResult(EnrollmentResult result) {
        return switch (result.status()) {
            case ACTIVATED -> EnrollmentResponse.from(result);
            case INVALID_CODE -> throw MfaProblemException.invalidCode("The submitted MFA code was not accepted.");
            case NOT_FOUND -> throw MfaProblemException.notFound("The requested MFA enrollment does not exist.");
            case ALREADY_ACTIVE ->
                throw MfaProblemException.enrollmentAlreadyActive("The MFA enrollment is already active.");
            case ENROLLED -> throw new IllegalStateException("Unexpected enrollment outcome for verification");
        };
    }

    private static ChallengeResponse mapChallengeCreationResult(ChallengeResult result) {
        return switch (result.status()) {
            case CREATED -> ChallengeResponse.from(result);
            case ENROLLMENT_NOT_FOUND ->
                throw MfaProblemException.notFound("The requested MFA enrollment does not exist.");
            case ENROLLMENT_NOT_ACTIVE ->
                throw MfaProblemException.enrollmentNotActive(
                        "The MFA enrollment must be active before a challenge can be created.");
            default -> throw new IllegalStateException("Unexpected challenge outcome for creation");
        };
    }

    private static ChallengeResponse mapChallengeVerificationResult(ChallengeResult result) {
        return switch (result.status()) {
            case VERIFIED -> ChallengeResponse.from(result);
            case INVALID_CODE ->
                throw MfaProblemException.invalidCode(
                        "The submitted MFA code was not accepted.", result.remainingAttempts());
            case EXPIRED -> throw MfaProblemException.challengeExpired("The MFA challenge is no longer valid.");
            case EXHAUSTED ->
                throw MfaProblemException.challengeExhausted(
                        "The MFA challenge has no remaining verification attempts.", result.remainingAttempts());
            case REPLAYED ->
                throw MfaProblemException.challengeReplayed(
                        "The MFA challenge was already consumed and cannot be replayed.");
            case NOT_FOUND -> throw MfaProblemException.notFound("The requested MFA challenge does not exist.");
            case ENROLLMENT_NOT_FOUND ->
                throw MfaProblemException.notFound("The MFA enrollment linked to this challenge does not exist.");
            case CREATED, ENROLLMENT_NOT_ACTIVE ->
                throw new IllegalStateException("Unexpected challenge outcome for verification");
        };
    }
}
