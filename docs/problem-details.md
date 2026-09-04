# MFA Problem Details Guidance

The MFA HTTP adapter publishes stable [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details on top of the application outcomes without coupling the domain to HTTP.

## Response Shape

The service uses `application/problem+json` and this shape for expected MFA failures:

```json
{
  "type": "urn:digital-bank:mfa:challenge-expired",
  "title": "MFA challenge expired",
  "status": 401,
  "detail": "The MFA challenge is no longer valid.",
  "instance": "/api/v1/mfa/challenges/opaque-id/verifications"
}
```

`detail` must not contain TOTP secrets, submitted codes, credential material, or customer-sensitive data. Challenge and enrollment ids should be treated as opaque identifiers and may be omitted from error details when disclosure would help enumeration.

## Stable Types

| Application outcome | HTTP status | Problem type | Meaning |
| --- | --- | --- | --- |
| Authentication required | `401` | `urn:digital-bank:mfa:authentication-required` | No bearer token or the bearer token failed authentication or JWT validation. |
| Access denied | `403` | `urn:digital-bank:mfa:access-denied` | The authenticated principal is missing the required internal MFA scope. |
| Validation failure | `400` | `https://digital-bank-java.local/problems/validation-error` | The request body or parameters failed boundary validation. |
| `NOT_FOUND` | `404` | `urn:digital-bank:mfa:resource-not-found` | The requested enrollment or challenge is unknown. |
| `INVALID_CODE` | `401` | `urn:digital-bank:mfa:invalid-code` | The code was invalid and the challenge remains open. |
| `EXPIRED` | `401` | `urn:digital-bank:mfa:challenge-expired` | The challenge failed closed at or after its expiry instant. |
| `EXHAUSTED` | `401` | `urn:digital-bank:mfa:challenge-exhausted` | The challenge consumed its bounded attempt budget. |
| `REPLAYED` | `401` | `urn:digital-bank:mfa:challenge-replayed` | A consumed challenge was presented again. |
| `ENROLLMENT_NOT_ACTIVE` | `409` | `urn:digital-bank:mfa:enrollment-not-active` | Challenge creation was attempted before enrollment activation. |
| `ALREADY_ACTIVE` | `409` | `urn:digital-bank:mfa:enrollment-already-active` | Enrollment activation was attempted after activation. |
| `BINDING_MISMATCH` | `409` | `urn:digital-bank:mfa:challenge-binding-mismatch` | A transfer challenge request or verification did not match its immutable transfer binding. |

Enrollment and challenge success responses never return TOTP secrets or authenticator provisioning material. Invalid-code and exhausted challenge responses may include `remainingAttempts` only when that metadata is available from the challenge workflow.

## Transport Ownership

The HTTP adapter now requires bearer JWT authentication on `/api/v1/mfa/**` through Spring Security's resource-server support. `spring.security.oauth2.resourceserver.jwt.issuer-uri` is required for the platform-owned trust configuration, and `jwk-set-uri` remains optional supplemental key material. The application validates the JWT issuer even when `jwk-set-uri` is configured explicitly. Rate limits, gateway routes, authenticator provisioning UX, and auth-service orchestration remain later integration work. The transfer-bound API is an internal MFA boundary; Transaction Service remains responsible for using verified assurance to continue the transfer workflow. The application/domain foundation remains usable without Spring MVC or an HTTP request context.
