# Multi-Factor Authentication Service

Multi-Factor Authentication Service is the Digital Bank Java platform boundary for multi-factor authentication workflows. This repository contains the deployable Spring Boot baseline plus a transport-neutral TOTP enrollment and MFA challenge foundation. No business HTTP routes are exposed in this wave.

## Implemented State

- Java 21 Spring Boot service named `mfa-service`.
- Spring Cloud Config Client integration for externalized runtime configuration.
- Actuator health, liveness, and readiness probes.
- Explicit internal OpenAPI metadata at `/v3/api-docs`.
- TOTP enrollment and activation application ports backed by an in-memory credential adapter.
- MFA challenge creation and verification with expiry, bounded attempts, and replay-safe terminal states.
- Standard `dev.samstevens.totp:totp:1.7.1` adapter for TOTP generation and verification.
- Non-root container image and hardened Helm deployment.
- Default SIT service port `8087`.

## Boundaries

This service will later own MFA policy and provider integration boundaries. The current application foundation does not own customer identity data, login orchestration, recovery codes, Kafka behavior, durable persistence, authorization decisions, or API transport.

TOTP enrollment results contain only an opaque enrollment id and lifecycle status. The generated secret is held only inside the credential store and is never returned by application results or aggregate `toString` output. Active challenge results contain only an opaque challenge id, lifecycle status, expiry, and remaining attempts.

Challenge verification is fail-closed at `now >= expiresAt`. Wrong codes consume one attempt, the final failed attempt moves the challenge to `EXHAUSTED`, a valid code moves it to `CONSUMED`, and later verification of a consumed challenge returns a replay outcome without calling the TOTP provider again. The default challenge TTL is `PT5M` and the default maximum is `5` attempts; both are configurable through `mfa.challenge.ttl` and `mfa.challenge.max-attempts` and remain subject to the 1 through 10 attempt bound.

The application services accept `java.time.Clock` and identifier-generator ports so unit tests can use fixed time and deterministic ids. See [Problem Details guidance](docs/problem-details.md) for the future inbound transport mapping. No Insomnia requests are included because this release adds no HTTP API.

MFA provider integrations must remain behind outbound ports and adapters when that work is approved and tracked. Never commit enrollment secrets, recovery codes, tokens, or production credentials to this repository.

## Runtime Configuration

Config Server supplies the effective runtime configuration. The service repository contains only the Config Client bootstrap and a local default port:

| Variable | Purpose | Default |
| --- | --- | --- |
| `CONFIG_SERVER_URL` | Config Server base URL | `http://localhost:8888` |
| `SPRING_PROFILES_ACTIVE` | Runtime environment profile | Spring `default` profile |
| `SERVER_PORT` | Workstation/container HTTP port | `8087` |

MFA foundation defaults:

| Property | Purpose | Default |
| --- | --- | --- |
| `mfa.challenge.ttl` | Challenge lifetime | `PT5M` |
| `mfa.challenge.max-attempts` | Maximum failed verification attempts | `5` |

The formal environments are `sit`, `uat`, and `prod`. `sit` runs on local Docker Desktop Kubernetes; `uat` and `prod` are future AWS environments. `local` is not an active environment or Spring profile. Workstation debugging uses the `sit` profile with temporary overrides against forwarded SIT dependencies.

Before deploying to SIT, the Config Server's backing `config-repo` should contain the `mfa-service` defaults and SIT override from config-repo PR [#32](https://github.com/digital-bank-java/config-repo/pull/32). Without those service-specific files, Config Server can still return shared configuration and the service can start with its local port default, but the intended `mfa-service` metadata is absent. The mandatory Config Client import still fails startup when Config Server itself is unavailable.

## Prerequisites

- Java 21.
- Network access to Maven Central for the initial dependency download.
- Docker Desktop for image builds.
- Helm 4 and Docker Desktop Kubernetes for local SIT deployment.
- A healthy Config Server for normal application startup.

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

The integration test disables Config Client and validates health, liveness, readiness, plus the OpenAPI title and contract version using a random application port.

## Run With Docker

Build the image:

```bash
docker build -t digital-bank-java/mfa-service:0.0.1 .
```

Run it against a reachable Config Server:

```bash
docker run --rm \
  --name digital-bank-mfa-service \
  --publish 8087:8087 \
  --env CONFIG_SERVER_URL=http://host.docker.internal:8888 \
  --env SPRING_PROFILES_ACTIVE=sit \
  digital-bank-java/mfa-service:0.0.1
```

The image runs as numeric non-root user and group `10001:10001` and uses `/tmp` for writable temporary files.

## Deploy To Local SIT

The Helm chart deploys into the `digital-bank-sit` namespace and expects Config Server to be available at `http://config-server:8888`.

```bash
helm lint helm --strict --values helm/values-sit.yaml

helm template mfa-service helm \
  --namespace digital-bank-sit \
  --values helm/values-sit.yaml \
  --set image.tag="0.0.1" \
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

Normal platform access should later flow through the API Gateway. No MFA business route is exposed by this foundation.

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

Auth-service orchestration, step-up policy, durable secret and challenge storage, external provider adapters, recovery codes, API Gateway routes, and Insomnia requests are later tasks linked to organization issues [#49](https://github.com/digital-bank-java/.github/issues/49), [#50](https://github.com/digital-bank-java/.github/issues/50), and [#51](https://github.com/digital-bank-java/.github/issues/51). The bootstrap PR [#1](https://github.com/digital-bank-java/mfa-service/pull/1) remains the required runtime/build dependency for this foundation.
