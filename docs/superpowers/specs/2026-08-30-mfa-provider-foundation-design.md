# MFA Provider Foundation Design

## Goal

Implement the transport-neutral application and domain foundation for TOTP enrollment and verification plus MFA challenge creation and verification. This change is stacked on the MFA service bootstrap and does not expose HTTP or gateway routes.

## Scope

- Generate and retain a TOTP credential through an outbound provider port.
- Keep the TOTP secret internal to the credential store and never return it from an application result or expose it through `toString`.
- Confirm a pending enrollment with a valid TOTP code, then allow verification only for an active enrollment.
- Create challenges with an expiry instant and bounded attempt count.
- Verify each challenge at most once, fail closed at expiry, and transition failed challenges to an exhausted state when attempts are consumed.
- Provide an in-memory adapter for this foundation. Persistence, provider integration beyond the standard TOTP library adapter, auth-service integration, step-up policy, recovery codes, and gateway routes remain later work.

## Architecture

The application layer owns use-case orchestration and depends on ports for enrollment storage, challenge storage, TOTP operations, and challenge-id generation. Domain models own lifecycle transitions and do not depend on Spring or transport types. An in-memory adapter supplies thread-safe storage for the foundation; the TOTP adapter delegates secret generation and code verification to `dev.samstevens.totp:totp:1.7.1`.

The service uses `java.time.Clock` for all current-time decisions and an injected challenge-id generator backed by secure randomness in production. Tests inject fixed clocks, deterministic ids, and a test TOTP provider. The standard library remains responsible for TOTP cryptography and time-step calculations.

## Enrollment Flow

1. `TotpEnrollmentService.enroll(subjectId)` validates the subject id, generates a secret through the TOTP port, creates an opaque enrollment id, stores the secret with `PENDING` state, and returns only the enrollment id and status.
2. `TotpEnrollmentService.verify(enrollmentId, code)` loads the enrollment, rejects unknown, expired, or already-active records, and delegates code verification with the injected clock instant.
3. A valid code changes the enrollment to `ACTIVE`. An invalid code leaves the enrollment pending and returns a failure result. The secret is never included in either result.

The in-memory credential adapter may read a secret internally for verification, but no public application contract returns it. Secret-bearing records have no public accessor, custom `toString`, or logging behavior.

## Challenge Flow

1. `MfaChallengeService.create(enrollmentId)` creates an opaque challenge id, stores the enrollment reference, creation instant, expiry instant, and configured maximum attempts, and returns only challenge metadata and remaining attempts.
2. `MfaChallengeService.verify(challengeId, code)` evaluates the challenge under one atomic state transition. It first rejects unknown, expired, consumed, or exhausted challenges. For an active challenge it verifies the code against the active enrollment.
3. A valid code transitions `OPEN` to `CONSUMED` and returns success. A wrong code increments failed attempts. When the maximum is reached, the challenge transitions to `EXHAUSTED`; all later requests fail without another TOTP check.
4. At `now >= expiresAt`, the challenge transitions or reports `EXPIRED` and fails even if the code is valid. A consumed challenge never replays successfully and produces a stable replay failure result.

Challenge state transitions are synchronized at the in-memory aggregate boundary so concurrent verification cannot consume one challenge twice or increment attempts beyond the limit. The persistent replacement must preserve the same compare-and-set semantics.

## Configuration

The application defaults are:

- `mfa.challenge.ttl=PT5M`
- `mfa.challenge.max-attempts=5`

Values are validated as a positive duration and an attempt count from 1 through 10. These defaults are local service defaults; environment-specific overrides remain owned by Config Server/config-repo.

## Error Model

The application returns typed outcome values for expected lifecycle failures instead of leaking provider exceptions. A future inbound adapter can map those outcomes to RFC 9457 Problem Details with stable types for unknown resources, invalid codes, expired challenges, exhausted attempts, replayed challenges, and invalid enrollment state. No HTTP error contract is introduced in this change.

## Testing

Unit coverage will exercise:

- enrollment creates a pending record without disclosing the secret;
- valid enrollment verification activates the record;
- invalid TOTP codes fail without activation;
- active enrollment verification accepts valid codes and rejects invalid codes;
- challenge creation stores the configured expiry and attempt budget;
- invalid codes decrement attempts and stop at exhaustion;
- expiry fails at and after the exact expiry instant;
- valid verification consumes a challenge and replay is rejected;
- fixed clock and deterministic id/randomness abstractions make outcomes reproducible.

The existing Spring Boot integration test remains limited to bootstrap health and OpenAPI metadata because no transport endpoint is introduced.

## Deferred Integration

Auth-service orchestration, step-up policy, customer identity ownership, durable secret/challenge storage, external provider adapters, recovery codes, API Gateway routes, and Insomnia requests are follow-up work. This foundation exposes only Java application ports and adapters inside `mfa-service`.
