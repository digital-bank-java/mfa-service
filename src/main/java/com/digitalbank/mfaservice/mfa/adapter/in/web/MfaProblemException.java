package com.digitalbank.mfaservice.mfa.adapter.in.web;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;

final class MfaProblemException extends RuntimeException {

    private final HttpStatus status;
    private final URI type;
    private final String title;
    private final Map<String, Object> properties;

    private MfaProblemException(
            HttpStatus status, URI type, String title, String detail, Map<String, Object> properties) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
        this.properties = properties;
    }

    static MfaProblemException notFound(String detail) {
        return new MfaProblemException(
                HttpStatus.NOT_FOUND,
                URI.create("urn:digital-bank:mfa:resource-not-found"),
                "MFA resource not found",
                detail,
                Map.of());
    }

    static MfaProblemException accessDenied(String detail) {
        return new MfaProblemException(
                HttpStatus.FORBIDDEN,
                URI.create("urn:digital-bank:mfa:access-denied"),
                "MFA access denied",
                detail,
                Map.of());
    }

    static MfaProblemException invalidCode(String detail) {
        return invalidCode(detail, null);
    }

    static MfaProblemException invalidCode(String detail, int remainingAttempts) {
        return invalidCode(detail, Integer.valueOf(remainingAttempts));
    }

    private static MfaProblemException invalidCode(String detail, Integer remainingAttempts) {
        return new MfaProblemException(
                HttpStatus.UNAUTHORIZED,
                URI.create("urn:digital-bank:mfa:invalid-code"),
                "Invalid MFA code",
                detail,
                remainingAttempts == null ? Map.of() : Map.of("remainingAttempts", remainingAttempts));
    }

    static MfaProblemException challengeExpired(String detail) {
        return new MfaProblemException(
                HttpStatus.UNAUTHORIZED,
                URI.create("urn:digital-bank:mfa:challenge-expired"),
                "MFA challenge expired",
                detail,
                Map.of());
    }

    static MfaProblemException challengeExhausted(String detail, int remainingAttempts) {
        return new MfaProblemException(
                HttpStatus.UNAUTHORIZED,
                URI.create("urn:digital-bank:mfa:challenge-exhausted"),
                "MFA challenge exhausted",
                detail,
                Map.of("remainingAttempts", remainingAttempts));
    }

    static MfaProblemException challengeReplayed(String detail) {
        return new MfaProblemException(
                HttpStatus.UNAUTHORIZED,
                URI.create("urn:digital-bank:mfa:challenge-replayed"),
                "MFA challenge replayed",
                detail,
                Map.of());
    }

    static MfaProblemException enrollmentNotActive(String detail) {
        return new MfaProblemException(
                HttpStatus.CONFLICT,
                URI.create("urn:digital-bank:mfa:enrollment-not-active"),
                "MFA enrollment not active",
                detail,
                Map.of());
    }

    static MfaProblemException enrollmentAlreadyActive(String detail) {
        return new MfaProblemException(
                HttpStatus.CONFLICT,
                URI.create("urn:digital-bank:mfa:enrollment-already-active"),
                "MFA enrollment already active",
                detail,
                Map.of());
    }

    HttpStatus status() {
        return status;
    }

    URI type() {
        return type;
    }

    String title() {
        return title;
    }

    Map<String, Object> properties() {
        return properties;
    }
}
