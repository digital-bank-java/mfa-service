# Multi-Factor Authentication Service

Multi-Factor Authentication Service is the Digital Bank Java platform boundary for multi-factor authentication workflows. This repository contains the deployable Spring Boot baseline, the transport-neutral TOTP enrollment and MFA challenge foundation, and the first HTTP input adapter for enrollment and verification workflows.

## Implemented State

- Java 21 Spring Boot service named `mfa-service`.
- Spring Cloud Config Client integration for externalized runtime configuration.
- Actuator health, liveness, and readiness probes.
- Explicit OpenAPI metadata at `/v3/api-docs`; service-local Swagger UI is disabled.
- Internal MFA HTTP routes protected by a JWT bearer-token resource-server boundary.
- TOTP enrollment and activation application ports backed by encrypted PostgreSQL persistence.
- MFA challenge creation and verification with expiry, bounded attempts, and replay-safe terminal states.
- Transfer-bound MFA challenge verification with durable `MfaAssuranceGranted.v1` outbox publication.
- Flyway-managed PostgreSQL schema with transaction-locked enrollment and challenge state transitions.
- HTTP APIs for MFA enrollment creation, enrollment verification, challenge creation, and challenge verification.
- Standard `dev.samstevens.totp:totp:1.7.1` adapter for TOTP generation and verification.
- Non-root container image and hardened Helm deployment.
- Default SIT service port `8087`.

## Boundaries

This service owns MFA provider state persistence and policy boundaries. It does not own customer identity data, login orchestration, recovery codes, or API Gateway routing. It publishes only the versioned transfer-assurance fact described below; it does not make transfer authorization decisions.

TOTP enrollment creation returns an authenticated, one-time `otpauth://` provisioning URI so an approved authenticator can be configured. The URI is sent with `Cache-Control: no-store` and `Pragma: no-cache`; it is never retrievable later, logged, or included in application-result and aggregate `toString` output. Enrollment verification and challenge responses contain only opaque ids, lifecycle status, expiry metadata, and remaining attempts.

Challenge verification is fail-closed at `now >= expiresAt`. Wrong codes consume one attempt, the final failed attempt moves the challenge to `EXHAUSTED`, a valid code moves it to `CONSUMED`, and later verification of a consumed challenge returns a replay outcome without calling the TOTP provider again. The default challenge TTL is `PT5M`, the maximum challenge TTL is `PT15M`, and the default maximum is `5` attempts; both settings are configurable through `mfa.challenge.ttl` and `mfa.challenge.max-attempts` and remain subject to their security bounds.

Enrollment verification is an atomic one-time transition: a valid code changes `PENDING` to `ACTIVE`, an invalid code leaves the enrollment pending, and repeated or competing verification after activation returns an already-active outcome without invoking the TOTP provider again.

The application services accept `java.time.Clock` and identifier-generator ports so unit tests can use fixed time and deterministic ids. See [Problem Details guidance](docs/problem-details.md) for the HTTP failure contract. This repository does not currently maintain a service-local Insomnia collection.

MFA provider integrations must remain behind outbound ports and adapters when that work is approved and tracked. TOTP secrets are encrypted with AES-GCM before they enter PostgreSQL. Never commit enrollment secrets, recovery codes, tokens, encryption keys, or production credentials to this repository.

## Runtime Configuration

Config Server supplies the effective runtime configuration. The service repository contains only the Config Client bootstrap and a local default port:

| Variable | Purpose | Default |
| --- | --- | --- |
| `CONFIG_SERVER_URL` | Config Server base URL | `http://localhost:8888` |
| `SPRING_PROFILES_ACTIVE` | Runtime environment profile | Spring `default` profile |
| `SERVER_PORT` | Workstation/container HTTP port | `8087` |
| `auth.jwt.secret` | Base64 HMAC secret shared with Auth Service in SIT | none |
| `auth.jwt.issuer` | HMAC token issuer used in SIT | none |
| `MFA_DATASOURCE_URL` | PostgreSQL JDBC URL; Config Server or Helm supplies the effective value | `jdbc:postgresql://postgres:5432/mfa_service` |
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials referenced by Config Server and Helm | none |
| `MFA_TOTP_ENCRYPTION_KEY` | Base64 encoding of a 32-byte AES key used to protect TOTP secrets at rest | required |
| `MFA_ASSURANCE_PUBLISHER_ENABLED` | Enables the opt-in Kafka outbox publisher | `false` |
| `MFA_KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses used only when assurance publishing is enabled | `localhost:9092` |

MFA foundation defaults:

| Property | Purpose | Default |
| --- | --- | --- |
| `mfa.challenge.ttl` | Challenge lifetime; maximum `PT15M` | `PT5M` |
| `mfa.challenge.max-attempts` | Maximum failed verification attempts; range `1` through `10` | `5` |

Internal endpoint authentication depends on standard Spring Security resource-server JWT configuration supplied by Config Server or explicit runtime overrides:

| Property | Purpose | Default |
| --- | --- | --- |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | OIDC issuer for internal service authentication | none |
| `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` | JWK set endpoint for internal service authentication | none |
| `mfa.totp.encryption-key` | AES-GCM key material; bind from `MFA_TOTP_ENCRYPTION_KEY` and keep outside Git | required |

When `spring.security.oauth2.resourceserver.jwt.issuer-uri` is configured, MFA uses OIDC discovery or the explicit JWK set and validates the token issuer. In local SIT, the service instead uses `auth.jwt.secret` and `auth.jwt.issuer` to validate the shared Auth Service HMAC token. HMAC mode requires a base64 secret decoding to at least 32 bytes; JWK material alone is not treated as sufficient trust configuration.

The formal environments are `sit`, `uat`, and `prod`. `sit` runs on local Docker Desktop Kubernetes; `uat` and `prod` are future AWS environments. `local` is not an active environment or Spring profile. Workstation debugging uses the `sit` profile with temporary overrides against forwarded SIT dependencies.

## HTTP API

The service exposes these routes directly on `mfa-service`:

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/mfa/enrollments` | Create a pending MFA enrollment for the authenticated JWT subject. |
| `POST` | `/api/v1/mfa/enrollments/{enrollmentId}/verifications` | Verify a pending enrollment with a 6-digit TOTP code and activate it. |
| `POST` | `/api/v1/mfa/challenges` | Create an MFA challenge for an active enrollment. |
| `POST` | `/api/v1/mfa/challenges/{challengeId}/verifications` | Verify an MFA challenge with a 6-digit TOTP code. |
| `POST` | `/api/v1/mfa/transfer-challenges` | Create a challenge bound to an immutable transfer risk decision and transfer intent. |
| `POST` | `/api/v1/mfa/transfer-challenges/{challengeId}/verifications` | Verify a transfer-bound challenge against its transfer and decision identifiers. |

All `/api/v1/mfa/**` routes require an internal bearer JWT. The enrollment creation response contains the one-time provisioning URI and no raw secret field; later enrollment and challenge responses contain only opaque ids, lifecycle status, expiry metadata, and remaining attempts. Clients must treat the URI as sensitive setup material and must not persist it in shared logs or exported workspace data.

Enrollment ownership is derived exclusively from the authenticated JWT `sub` claim. The legacy `subjectId` request field remains accepted for client compatibility but is ignored and is not an authorization input. Enrollment and challenge identifiers are also checked against that authenticated subject; missing and foreign resources use the same controlled `404` resource-not-found problem.

Representative requests:

```bash
curl --request POST http://localhost:8087/api/v1/mfa/enrollments \
  --header 'Authorization: Bearer <internal-jwt>' \
  --header 'Content-Type: application/json' \
  --data '{}'

curl --request POST http://localhost:8087/api/v1/mfa/enrollments/<enrollment-id>/verifications \
  --header 'Authorization: Bearer <internal-jwt>' \
  --header 'Content-Type: application/json' \
  --data '{
    "code": "123456"
  }'

curl --request POST http://localhost:8087/api/v1/mfa/challenges \
  --header 'Authorization: Bearer <internal-jwt>' \
  --header 'Content-Type: application/json' \
  --data '{
    "enrollmentId": "<enrollment-id>"
  }'

curl --request POST http://localhost:8087/api/v1/mfa/challenges/<challenge-id>/verifications \
  --header 'Authorization: Bearer <internal-jwt>' \
  --header 'Content-Type: application/json' \
  --data '{
    "code": "123456"
  }'
```

Error responses use `application/problem+json`. Authentication failures return `401` with `urn:digital-bank:mfa:authentication-required`, authorization failures return `403` with `urn:digital-bank:mfa:access-denied`, validation failures return `400`, unknown resources return `404`, enrollment state conflicts return `409`, and invalid or expired challenge verification outcomes return `401`.

`/v3/api-docs` remains available for internal machine-readable contract publication. Service-local Swagger UI is disabled; the platform-owned interactive documentation surface belongs at the API Gateway.

Successful transfer-bound verification writes one `MfaAssuranceGranted.v1` event to the PostgreSQL outbox in the same transaction that consumes the challenge. The event is published to `mfa.assurance.granted.v1` with the persisted event id and payload; retries are at-least-once and consumers must deduplicate by `eventId`. Invalid, expired, replayed, or binding-mismatched verification never writes assurance.

The enrollment creation response is the platform-owned authenticator provisioning path. It is authenticated to the enrollment owner and returns the URI only at creation time. There is no secret-retrieval endpoint, and the response object and application result redact the URI from `toString()` output. Clients must discard the URI after authenticator provisioning.

Before deploying to SIT, the Config Server's backing `config-repo` should contain the `mfa-service` defaults and SIT override from config-repo PR [#32](https://github.com/digital-bank-java/config-repo/pull/32). Without those service-specific files, Config Server can still return shared configuration and the service can start with its local port default, but the intended `mfa-service` metadata is absent. The mandatory Config Client import still fails startup when Config Server itself is unavailable.

SIT also requires a PostgreSQL database named `mfa_service` and an externally managed Kubernetes Secret named `mfa-service-secrets` with key `MFA_TOTP_ENCRYPTION_KEY`. Generate a development-only key outside Git, for example:

```bash
kubectl create secret generic mfa-service-secrets \
  --namespace digital-bank-sit \
  --from-literal=MFA_TOTP_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  --dry-run=client --output=yaml | kubectl apply -f -
```

The Helm chart reads PostgreSQL credentials from the existing `postgres` Secret and reads only the encryption key from `mfa-service-secrets`. Do not reuse a production key in SIT, and do not put either secret value in Helm values, Config Server Git, logs, or test assertions. Flyway creates the MFA tables on service startup; existing rows remain encrypted and are not rewritten by migration.

Kafka assurance publishing is disabled by default. For SIT, opt in only after the contract topic and broker are provisioned, for example with `--set assurancePublisher.enabled=true --set kafka.bootstrapServers=kafka:9092`; no Kafka credentials or secret values belong in this chart.

Persistence details, row-locking behavior, and the key boundary are documented in [MFA persistence](docs/mfa-persistence.md).

## Prerequisites

- Java 21.
- Network access to Maven Central for the initial dependency download.
- Docker Desktop for image builds.
- Helm 4 and Docker Desktop Kubernetes for local SIT deployment.
- A healthy Config Server for normal application startup.
- Platform-provided JWT resource-server configuration for authenticated MFA routes.

The Maven Wrapper is included, so a global Maven installation is not required.

```bash
java -version
./mvnw --version
docker version
helm version --short
kubectl config current-context
```

## Build And Test

Run the unit-test phase:

```bash
./mvnw --batch-mode --no-transfer-progress test
```

Run integration tests and package verification:

```bash
./mvnw --batch-mode --no-transfer-progress verify -DskipUnitTests=true
```

The integration tests disable Config Client and validate health, liveness, readiness, the MFA HTTP contract, the disabled Swagger UI surface, and the published OpenAPI document through real random-port HTTP requests.

## Run With Docker

Build the image:

```bash
docker build -t digital-bank-java/mfa-service:0.0.3 .
```

Run it against a reachable Config Server and JWT issuer/JWK configuration:

```bash
docker run --rm \
  --name digital-bank-mfa-service \
  --publish 8087:8087 \
  --env CONFIG_SERVER_URL=http://host.docker.internal:8888 \
  --env SPRING_PROFILES_ACTIVE=sit \
  --env SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://issuer.example.internal \
  digital-bank-java/mfa-service:0.0.3
```

The image runs as numeric non-root user and group `10001:10001` and uses `/tmp` for writable temporary files.

## Deploy To Local SIT

The Helm chart deploys into the `digital-bank-sit` namespace and expects Config Server to be available at `http://config-server:8888`.

```bash
helm lint helm --strict --values helm/values-sit.yaml

helm template mfa-service helm \
  --namespace digital-bank-sit \
  --values helm/values-sit.yaml \
  --set image.tag="0.0.3" \
  | kubectl apply --dry-run=client -f -

helm upgrade --install mfa-service helm \
  --namespace digital-bank-sit \
  --create-namespace \
  --values helm/values-sit.yaml \
  --wait \
  --timeout 5m
```

Check the rollout and service health:

```bash
kubectl rollout status deployment/mfa-service \
  --namespace digital-bank-sit --timeout=180s

kubectl port-forward service/mfa-service 18087:8087 \
  --namespace digital-bank-sit
```

In another terminal:

```bash
curl --fail http://localhost:18087/actuator/health
curl --fail http://localhost:18087/v3/api-docs
```

Normal platform access should later flow through the API Gateway. This repository only exposes the service-local HTTP input adapter and does not include gateway route configuration.

## CI

The GitHub Actions workflow runs Maven verification and Helm validation. A container job then builds the image, verifies the non-root user, and smoke-tests health against a disposable mock Config Server. Third-party actions are pinned to immutable commit SHAs.

## Workstation Debugging Against SIT

Use the organization [workstation debugging procedure](https://github.com/digital-bank-java/.github/blob/main/docs/workstation-debugging-against-sit.md). Confirm SIT dependencies first, temporarily scale down the Kubernetes MFA deployment, use the `sit` profile, and provide temporary Config Server overrides through environment variables. Restore the deployment after debugging.

## Contribution Workflow

Use a dedicated branch and pull request; do not commit directly to `main`. Before opening a PR:

```bash
git status
./mvnw verify
git diff --check
```

All changes require review by the CODEOWNERS maintainer. Never commit credentials, tokens, MFA secrets, recovery codes, customer information, or production endpoints.

## Follow-Up Integration

Auth-service orchestration, step-up policy, external provider adapters, recovery codes, API Gateway routes, and any future shared API client collections remain follow-up work linked to organization issues [#49](https://github.com/digital-bank-java/.github/issues/49), [#50](https://github.com/digital-bank-java/.github/issues/50), and [#51](https://github.com/digital-bank-java/.github/issues/51). The bootstrap PR [#1](https://github.com/digital-bank-java/mfa-service/pull/1) remains the required runtime/build dependency for this foundation.

## Operational Logging

The service emits one-line ECS JSON console events and propagates the bounded
`X-Correlation-ID` boundary defined in the organization [structured logging and redaction contract](https://github.com/digital-bank-java/.github/blob/main/docs/structured-logging-and-redaction.md). OTP values, secrets, challenge data, and identity data are not logged.
