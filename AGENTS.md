# Multi-Factor Authentication Service Agent Guide

## Purpose

`mfa-service` is the foundation for multi-factor authentication workflows and provider integration boundaries. It contains transport-neutral TOTP enrollment and bounded challenge application services with PostgreSQL-backed adapters plus the HTTP input adapter for enrollment and challenge verification.

## Current Boundaries

- Owns service bootstrap, Config Client, health probes, OpenAPI metadata, packaging, and deployment.
- Owns TOTP enrollment activation, challenge state transitions, and the HTTP input adapter that exposes those workflows.
- PostgreSQL stores are the runtime adapters. The in-memory stores remain test-only foundation adapters and must not be wired into deployed profiles.
- Flyway owns the `mfa_enrollments` and `mfa_challenges` schema. Application verification runs inside database transactions and the stores lock rows before state transitions.
- TOTP secrets are encrypted at rest and returned only inside the one-time enrollment provisioning URI. They must never be logged, retrievable later, or included in subsequent lifecycle responses.
- TOTP secrets are encrypted with AES-GCM before persistence. The 32-byte base64 encryption key is supplied by an external runtime secret and must never be committed.
- Challenge verification must fail at `now >= expiresAt`, enforce the attempt limit, and reject replay after consumption.
- Does not own customer identity data, login orchestration, recovery codes, Kafka behavior, or authorization decisions.
- Public MFA routes require a supporting issue, boundary DTO validation, and documented Problem Details mapping.

## Commands

```bash
./mvnw test
./mvnw verify
docker build -t digital-bank-java/mfa-service:<tag> .
helm lint helm --strict --values helm/values-sit.yaml
```

## Runtime

- Service port: `8087`.
- Config name: `mfa-service`.
- SIT namespace: `digital-bank-sit`.
- Runtime profiles: `sit`, `uat`, and `prod`; `local` is retired.
- Runtime configuration is externalized through Config Server and `config-repo`.
- Runtime persistence requires PostgreSQL datasource settings and `MFA_TOTP_ENCRYPTION_KEY`; missing encryption-key configuration must fail startup rather than fall back to plaintext.
- Foundation defaults are `mfa.challenge.ttl=PT5M` and `mfa.challenge.max-attempts=5`.

## Testing

Use unit tests for isolated application/domain behavior and inject fixed clocks, ids, and TOTP fakes for deterministic lifecycle coverage. Use the database-backed integration test for Flyway/schema and encrypted persistence coverage. Keep `./mvnw verify` green before merge.

## Architecture

Use hexagonal boundaries for MFA workflows. Inbound web adapters should handle transport only; outbound adapters should isolate MFA providers and other infrastructure. Never place business logic in controllers, Helm templates, or CI workflows. Treat TOTP seeds, recovery codes, and provider credentials as secrets managed outside Git. See `docs/problem-details.md` for the future transport error mapping.

## Workflow

Every change requires a supporting GitHub issue, a dedicated branch, and a non-draft pull request. Assign active tasks to `ramioooz`, use the native Issue Type, attach work to the correct sprint/epic, and never merge directly to `main`.
