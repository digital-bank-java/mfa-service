# Multi-Factor Authentication Service Agent Guide

## Purpose

`mfa-service` is the foundation for future multi-factor authentication workflows and provider integration boundaries. It is intentionally a bootstrap service until MFA business behavior is separately planned and tracked.

## Current Boundaries

- Owns service bootstrap, Config Client, health probes, OpenAPI metadata, packaging, and deployment.
- Does not own customer identity data, login orchestration, TOTP secrets, recovery codes, Kafka behavior, persistence, or authorization decisions.
- Do not add public MFA routes without a supporting issue and API contract work.

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

## Testing

Use unit tests for isolated application/domain behavior when those layers exist. Use integration tests for HTTP and infrastructure-backed behavior. Keep `./mvnw verify` green before merge.

## Architecture

Use hexagonal boundaries for future MFA workflows. Inbound web adapters should handle transport only; outbound adapters should isolate MFA providers and other infrastructure. Never place business logic in controllers, Helm templates, or CI workflows. Treat TOTP seeds, recovery codes, and provider credentials as secrets managed outside Git.

## Workflow

Every change requires a supporting GitHub issue, a dedicated branch, and a non-draft pull request. Assign active tasks to `ramioooz`, use the native Issue Type, attach work to the correct sprint/epic, and never merge directly to `main`.
