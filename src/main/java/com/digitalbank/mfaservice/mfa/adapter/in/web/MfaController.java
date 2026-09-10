package com.digitalbank.mfaservice.mfa.adapter.in.web;

import com.digitalbank.mfaservice.mfa.application.ChallengeResult;
import com.digitalbank.mfaservice.mfa.application.EnrollmentResult;
import com.digitalbank.mfaservice.mfa.application.MfaChallengeService;
import com.digitalbank.mfaservice.mfa.application.TotpEnrollmentService;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    private static final String CREATE_ENROLLMENT_VALIDATION_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Request validation failed",
              "instance": "/api/v1/mfa/enrollments",
              "errors": [
                {
                  "field": "request",
                  "message": "Malformed JSON request"
                }
              ]
            }
            """;

    private static final String VERIFY_ENROLLMENT_VALIDATION_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Request validation failed",
              "instance": "/api/v1/mfa/enrollments/enrollment-1/verifications",
              "errors": [
                {
                  "field": "code",
                  "message": "must contain exactly 6 digits"
                }
              ]
            }
            """;

    private static final String CREATE_CHALLENGE_VALIDATION_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Request validation failed",
              "instance": "/api/v1/mfa/challenges",
              "errors": [
                {
                  "field": "enrollmentId",
                  "message": "must not be blank"
                }
              ]
            }
            """;

    private static final String VERIFY_CHALLENGE_VALIDATION_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Request validation failed",
              "instance": "/api/v1/mfa/challenges/challenge-1/verifications",
              "errors": [
                {
                  "field": "code",
                  "message": "must contain exactly 6 digits"
                }
              ]
            }
            """;

    private static final String CREATE_ENROLLMENT_AUTHENTICATION_REQUIRED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:authentication-required",
              "title": "MFA authentication required",
              "status": 401,
              "detail": "Authentication is required to access this MFA resource.",
              "instance": "/api/v1/mfa/enrollments"
            }
            """;

    private static final String VERIFY_ENROLLMENT_AUTHENTICATION_REQUIRED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:authentication-required",
              "title": "MFA authentication required",
              "status": 401,
              "detail": "Authentication is required to access this MFA resource.",
              "instance": "/api/v1/mfa/enrollments/enrollment-1/verifications"
            }
            """;

    private static final String CREATE_CHALLENGE_AUTHENTICATION_REQUIRED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:authentication-required",
              "title": "MFA authentication required",
              "status": 401,
              "detail": "Authentication is required to access this MFA resource.",
              "instance": "/api/v1/mfa/challenges"
            }
            """;

    private static final String VERIFY_CHALLENGE_AUTHENTICATION_REQUIRED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:authentication-required",
              "title": "MFA authentication required",
              "status": 401,
              "detail": "Authentication is required to access this MFA resource.",
              "instance": "/api/v1/mfa/challenges/challenge-1/verifications"
            }
            """;

    private static final String CREATE_ENROLLMENT_ACCESS_DENIED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:access-denied",
              "title": "MFA access denied",
              "status": 403,
              "detail": "The authenticated principal is not allowed to access this MFA resource.",
              "instance": "/api/v1/mfa/enrollments"
            }
            """;

    private static final String VERIFY_ENROLLMENT_ACCESS_DENIED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:access-denied",
              "title": "MFA access denied",
              "status": 403,
              "detail": "The authenticated principal is not allowed to access this MFA resource.",
              "instance": "/api/v1/mfa/enrollments/enrollment-1/verifications"
            }
            """;

    private static final String CREATE_CHALLENGE_ACCESS_DENIED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:access-denied",
              "title": "MFA access denied",
              "status": 403,
              "detail": "The authenticated principal is not allowed to access this MFA resource.",
              "instance": "/api/v1/mfa/challenges"
            }
            """;

    private static final String VERIFY_CHALLENGE_ACCESS_DENIED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:access-denied",
              "title": "MFA access denied",
              "status": 403,
              "detail": "The authenticated principal is not allowed to access this MFA resource.",
              "instance": "/api/v1/mfa/challenges/challenge-1/verifications"
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

    private static final String CHALLENGE_EXPIRED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:challenge-expired",
              "title": "MFA challenge expired",
              "status": 401,
              "detail": "The MFA challenge is no longer valid.",
              "instance": "/api/v1/mfa/challenges/challenge-1/verifications"
            }
            """;

    private static final String CHALLENGE_EXHAUSTED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:challenge-exhausted",
              "title": "MFA challenge exhausted",
              "status": 401,
              "detail": "The MFA challenge has no remaining verification attempts.",
              "instance": "/api/v1/mfa/challenges/challenge-1/verifications",
              "remainingAttempts": 0
            }
            """;

    private static final String CHALLENGE_REPLAYED_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:challenge-replayed",
              "title": "MFA challenge replayed",
              "status": 401,
              "detail": "The MFA challenge was already consumed and cannot be replayed.",
              "instance": "/api/v1/mfa/challenges/challenge-1/verifications"
            }
            """;

    private static final String VERIFY_ENROLLMENT_RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:resource-not-found",
              "title": "MFA resource not found",
              "status": 404,
              "detail": "The requested MFA enrollment does not exist.",
              "instance": "/api/v1/mfa/enrollments/enrollment-404/verifications"
            }
            """;

    private static final String CREATE_CHALLENGE_RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:resource-not-found",
              "title": "MFA resource not found",
              "status": 404,
              "detail": "The requested MFA enrollment does not exist.",
              "instance": "/api/v1/mfa/challenges"
            }
            """;

    private static final String VERIFY_CHALLENGE_RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE = """
            {
              "type": "urn:digital-bank:mfa:resource-not-found",
              "title": "MFA resource not found",
              "status": 404,
              "detail": "The requested MFA challenge does not exist.",
              "instance": "/api/v1/mfa/challenges/challenge-404/verifications"
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
            responseCode = "401",
            description = "Authentication required",
            headers =
                    @Header(
                            name = "WWW-Authenticate",
                            description = "Bearer authentication challenge returned for authentication failures.",
                            schema = @Schema(type = "string", example = "Bearer")),
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "authentication-required",
                                            value = CREATE_ENROLLMENT_AUTHENTICATION_REQUIRED_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "403",
            description = "Access denied",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "access-denied",
                                            value = CREATE_ENROLLMENT_ACCESS_DENIED_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "validation-error",
                                            value = CREATE_ENROLLMENT_VALIDATION_PROBLEM_EXAMPLE)))
    ResponseEntity<EnrollmentResponse> createEnrollment(
            Authentication authentication, @Valid @RequestBody CreateEnrollmentRequest request) {
        var result = enrollmentService.enroll(authenticatedSubject(authentication));
        var response = EnrollmentResponse.from(result);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
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
                            examples =
                                    @ExampleObject(
                                            name = "validation-error",
                                            value = VERIFY_ENROLLMENT_VALIDATION_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required or invalid MFA code",
            headers =
                    @Header(
                            name = "WWW-Authenticate",
                            description =
                                    "Bearer authentication challenge returned when the bearer token is invalid or absent.",
                            schema = @Schema(type = "string", example = "Bearer")),
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                @ExampleObject(
                                        name = "authentication-required",
                                        value = VERIFY_ENROLLMENT_AUTHENTICATION_REQUIRED_PROBLEM_EXAMPLE),
                                @ExampleObject(name = "invalid-code", value = ENROLLMENT_INVALID_CODE_PROBLEM_EXAMPLE)
                            }))
    @ApiResponse(
            responseCode = "403",
            description = "Access denied",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "access-denied",
                                            value = VERIFY_ENROLLMENT_ACCESS_DENIED_PROBLEM_EXAMPLE)))
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
                                            value = VERIFY_ENROLLMENT_RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE)))
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
            Authentication authentication,
            @PathVariable String enrollmentId,
            @Valid @RequestBody VerifyTotpCodeRequest request) {
        var result = enrollmentService.verify(
                new EnrollmentId(enrollmentId), authenticatedSubject(authentication), request.code());
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
                            examples =
                                    @ExampleObject(
                                            name = "validation-error",
                                            value = CREATE_CHALLENGE_VALIDATION_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required",
            headers =
                    @Header(
                            name = "WWW-Authenticate",
                            description = "Bearer authentication challenge returned for authentication failures.",
                            schema = @Schema(type = "string", example = "Bearer")),
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "authentication-required",
                                            value = CREATE_CHALLENGE_AUTHENTICATION_REQUIRED_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "403",
            description = "Access denied",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "access-denied",
                                            value = CREATE_CHALLENGE_ACCESS_DENIED_PROBLEM_EXAMPLE)))
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
                                            value = CREATE_CHALLENGE_RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE)))
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
    ResponseEntity<ChallengeResponse> createChallenge(
            Authentication authentication, @Valid @RequestBody CreateChallengeRequest request) {
        var result =
                challengeService.create(new EnrollmentId(request.enrollmentId()), authenticatedSubject(authentication));
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
                            examples =
                                    @ExampleObject(
                                            name = "validation-error",
                                            value = VERIFY_CHALLENGE_VALIDATION_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "401",
            description = "Authentication required or verification failed",
            headers =
                    @Header(
                            name = "WWW-Authenticate",
                            description =
                                    "Bearer authentication challenge returned when the bearer token is invalid or absent.",
                            schema = @Schema(type = "string", example = "Bearer")),
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                @ExampleObject(
                                        name = "authentication-required",
                                        value = VERIFY_CHALLENGE_AUTHENTICATION_REQUIRED_PROBLEM_EXAMPLE),
                                @ExampleObject(name = "invalid-code", value = CHALLENGE_INVALID_CODE_PROBLEM_EXAMPLE),
                                @ExampleObject(name = "expired", value = CHALLENGE_EXPIRED_PROBLEM_EXAMPLE),
                                @ExampleObject(name = "exhausted", value = CHALLENGE_EXHAUSTED_PROBLEM_EXAMPLE),
                                @ExampleObject(name = "replayed", value = CHALLENGE_REPLAYED_PROBLEM_EXAMPLE)
                            }))
    @ApiResponse(
            responseCode = "403",
            description = "Access denied",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "access-denied",
                                            value = VERIFY_CHALLENGE_ACCESS_DENIED_PROBLEM_EXAMPLE)))
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
                                            value = VERIFY_CHALLENGE_RESOURCE_NOT_FOUND_PROBLEM_EXAMPLE)))
    ResponseEntity<ChallengeResponse> verifyChallenge(
            Authentication authentication,
            @PathVariable String challengeId,
            @Valid @RequestBody VerifyTotpCodeRequest request) {
        var result = challengeService.verify(
                new ChallengeId(challengeId), authenticatedSubject(authentication), request.code());
        return ResponseEntity.ok(mapChallengeVerificationResult(result));
    }

    @PostMapping("/api/v1/mfa/transfer-challenges")
    @Operation(summary = "Create a transfer-bound MFA challenge")
    ResponseEntity<TransferChallengeResponse> createTransferChallenge(
            Authentication authentication, @Valid @RequestBody CreateTransferChallengeRequest request) {
        var subjectId = authenticatedSubject(authentication);
        var binding = new com.digitalbank.mfaservice.mfa.domain.TransferChallengeBinding(
                request.transferId(),
                request.reservationRequestId(),
                request.decisionId(),
                request.decisionRequestId(),
                subjectId,
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.currency(),
                request.policyVersion(),
                request.correlationId());
        var result =
                challengeService.createTransferChallenge(new EnrollmentId(request.enrollmentId()), subjectId, binding);
        var response = mapTransferChallengeCreationResult(result);
        var builder = ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replay", Boolean.toString(result.replayed()));
        if (!result.replayed()) {
            builder.location(URI.create("/api/v1/mfa/transfer-challenges/" + response.challengeId()));
        }
        return builder.body(response);
    }

    @PostMapping("/api/v1/mfa/transfer-challenges/{challengeId}/verifications")
    @Operation(summary = "Verify a transfer-bound MFA challenge")
    ResponseEntity<TransferChallengeResponse> verifyTransferChallenge(
            Authentication authentication,
            @PathVariable String challengeId,
            @Valid @RequestBody VerifyTransferChallengeRequest request) {
        var subjectId = authenticatedSubject(authentication);
        var result = challengeService.verifyTransferChallenge(
                new ChallengeId(challengeId), subjectId, request.transferId(), request.decisionId(), request.code());
        return ResponseEntity.ok(mapTransferChallengeVerificationResult(result));
    }

    private static String authenticatedSubject(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw MfaProblemException.accessDenied(
                    "A JWT-authenticated principal with a subject is required to access this MFA resource.");
        }
        var subject = jwtAuthentication.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw MfaProblemException.accessDenied(
                    "A JWT-authenticated principal with a subject is required to access this MFA resource.");
        }
        return subject;
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
            case BINDING_MISMATCH ->
                throw MfaProblemException.challengeBindingMismatch(
                        "The transfer binding does not match the existing MFA challenge.");
        };
    }

    private static TransferChallengeResponse mapTransferChallengeCreationResult(ChallengeResult result) {
        return switch (result.status()) {
            case CREATED -> TransferChallengeResponse.from(result);
            case ENROLLMENT_NOT_FOUND ->
                throw MfaProblemException.notFound("The requested MFA enrollment does not exist.");
            case ENROLLMENT_NOT_ACTIVE ->
                throw MfaProblemException.enrollmentNotActive(
                        "The MFA enrollment must be active before a challenge can be created.");
            case BINDING_MISMATCH ->
                throw MfaProblemException.challengeBindingMismatch(
                        "The transfer binding does not match the existing MFA challenge.");
            default -> throw new IllegalStateException("Unexpected transfer challenge outcome");
        };
    }

    private static TransferChallengeResponse mapTransferChallengeVerificationResult(ChallengeResult result) {
        return switch (result.status()) {
            case VERIFIED -> TransferChallengeResponse.from(result);
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
            case BINDING_MISMATCH ->
                throw MfaProblemException.challengeBindingMismatch(
                        "The transfer binding does not match the existing MFA challenge.");
            case NOT_FOUND -> throw MfaProblemException.notFound("The requested MFA challenge does not exist.");
            case ENROLLMENT_NOT_FOUND ->
                throw MfaProblemException.notFound("The MFA enrollment linked to this challenge does not exist.");
            case CREATED, ENROLLMENT_NOT_ACTIVE ->
                throw new IllegalStateException("Unexpected transfer verification outcome");
        };
    }
}
