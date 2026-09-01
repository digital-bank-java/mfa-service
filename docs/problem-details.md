# MFA Problem Details Guidance

This release does not expose enrollment or challenge HTTP routes. The application services return typed outcomes so a future inbound adapter can publish stable [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details without coupling the domain to HTTP.

## Response Shape

Future HTTP adapters should use `application/problem+json` and this shape for expected MFA failures:

```json
{
  "type": "urn:digital-bank:mfa:challenge-expired",
  "title": "MFA challenge expired",
  "status": 401,
  "detail": "The MFA challenge is no longer valid.",
  "instance": "/mfa/challenges/opaque-id"
}
```

`detail` must not contain TOTP secrets, submitted codes, credential material, or customer-sensitive data. Challenge and enrollment ids should be treated as opaque identifiers and may be omitted from error details when disclosure would help enumeration.

## Stable Types

| Application outcome | Problem type | Meaning |
| --- | --- | --- |
| `NOT_FOUND` | `urn:digital-bank:mfa:resource-not-found` | The requested enrollment or challenge is unknown. |
| `INVALID_CODE` | `urn:digital-bank:mfa:invalid-code` | The code was invalid and the challenge remains open. |
| `EXPIRED` | `urn:digital-bank:mfa:challenge-expired` | The challenge failed closed at or after its expiry instant. |
| `EXHAUSTED` | `urn:digital-bank:mfa:challenge-exhausted` | The challenge consumed its bounded attempt budget. |
| `REPLAYED` | `urn:digital-bank:mfa:challenge-replayed` | A consumed challenge was presented again. |
| `ENROLLMENT_NOT_ACTIVE` | `urn:digital-bank:mfa:enrollment-not-active` | Challenge creation was attempted before enrollment activation. |
| `ALREADY_ACTIVE` | `urn:digital-bank:mfa:enrollment-already-active` | Enrollment activation was attempted after activation. |

Successful enrollment and challenge operations return opaque metadata and lifecycle status only. They never return the TOTP secret.

## Transport Ownership

Mapping HTTP status codes, authentication headers, rate limits, gateway routes, and auth-service orchestration belongs to a later integration task. The application/domain foundation must remain usable without Spring MVC or an HTTP request context.
