# MFA Provider Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a transport-neutral TOTP enrollment/verification and bounded MFA challenge foundation on top of the bootstrap branch.

**Architecture:** Domain aggregates own enrollment and challenge lifecycle transitions. Application services orchestrate those aggregates through ports, while in-memory stores and a `dev.samstevens.totp:totp:1.7.1` adapter provide the initial adapters. No HTTP controller, gateway route, persistence adapter, auth-service integration, or step-up policy is added.

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring configuration properties, `dev.samstevens.totp:totp:1.7.1`, JUnit 5, AssertJ, Maven, Helm.

**Spec:** `docs/superpowers/specs/2026-08-30-mfa-provider-foundation-design.md`

## Global Constraints

- Use `java.time.Clock` for all current-time decisions.
- Use an injected challenge/enrollment identifier generator backed by secure randomness in production.
- TOTP cryptography and time-step calculations must be delegated to `dev.samstevens.totp:totp:1.7.1`.
- Enrollment results and challenge results must never contain TOTP secrets.
- Challenge verification must fail at `now >= expiresAt`, consume at most once, and stop at the configured attempt limit.
- Default `mfa.challenge.ttl` is `PT5M`; default `mfa.challenge.max-attempts` is `5`; attempts must be 1 through 10.
- No HTTP endpoints or Insomnia requests are introduced in this wave.

---

### Task 1: Add TOTP dependency and failing enrollment tests

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/digitalbank/mfaservice/mfa/application/TotpEnrollmentServiceTest.java`
- Create: `src/test/java/com/digitalbank/mfaservice/mfa/application/TestMfaFixtures.java`

**Interfaces:**
- Tests define the expected `TotpEnrollmentService` constructor, enrollment outcomes, and store/provider ports consumed by later tasks.

- [x] **Step 1: Add the standard TOTP dependency**

Add this Maven dependency under application dependencies:

```xml
<dependency>
  <groupId>dev.samstevens.totp</groupId>
  <artifactId>totp</artifactId>
  <version>1.7.1</version>
</dependency>
```

- [x] **Step 2: Write the failing enrollment tests**

Cover these behaviors with direct service tests and a deterministic fake provider/id generator: enrollment creates `PENDING`, valid code activates it, invalid code leaves it pending, unknown enrollment fails, and returned values plus `toString` do not contain the generated secret.

- [x] **Step 3: Run the focused test to verify the expected compile failure**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TotpEnrollmentServiceTest test`

Expected: compilation fails because the application/domain contracts do not exist yet.

- [x] **Step 4: Commit the dependency and red tests**

```bash
git add pom.xml src/test/java/com/digitalbank/mfaservice/mfa/application
git commit -m "test: define totp enrollment contract"
```

### Task 2: Implement enrollment domain, ports, and in-memory adapter

**Files:**
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/EnrollmentStatus.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/EnrollmentId.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/TotpCodeVerifier.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/TotpCredential.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/Enrollment.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/application/EnrollmentResult.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/application/port/TotpProvider.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/application/port/EnrollmentStore.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/application/port/MfaIdentifierGenerator.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/application/TotpEnrollmentService.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/adapter/memory/InMemoryEnrollmentStore.java`

**Interfaces:**
- `TotpProvider.generateSecret(): String` and `TotpProvider.verify(String, String, Instant): boolean`.
- `EnrollmentStore.save(Enrollment): void` and `EnrollmentStore.find(EnrollmentId): Optional<Enrollment>`.
- `MfaIdentifierGenerator.newEnrollmentId(): EnrollmentId` and `newChallengeId(): ChallengeId`.
- `TotpEnrollmentService.enroll(String): EnrollmentResult` and `verify(EnrollmentId, String): EnrollmentResult`.

- [x] **Step 1: Implement the minimal domain and port types required by the tests**

Keep the secret in a private credential implementation held by `Enrollment`; expose only id, subject, creation instant, and status. Make activation synchronized and reject activation after the record is already active.

- [x] **Step 2: Implement `TotpEnrollmentService`**

Inject `EnrollmentStore`, `TotpProvider`, `MfaIdentifierGenerator`, and `Clock`. Use the clock instant for creation and verification. Return typed outcomes without a secret.

- [x] **Step 3: Implement `InMemoryEnrollmentStore`**

Use a `ConcurrentHashMap<EnrollmentId, Enrollment>`. Do not add logging or public secret accessors. Map duplicate identifiers to an explicit duplicate failure rather than replacing a credential.

- [x] **Step 4: Run the focused enrollment tests**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TotpEnrollmentServiceTest test`

Expected: all enrollment tests pass.

- [x] **Step 5: Commit the enrollment slice**

```bash
git add src/main/java/com/digitalbank/mfaservice/mfa
git commit -m "feat: add totp enrollment foundation"
```

### Task 3: Add failing challenge lifecycle tests and implement bounded state

**Files:**
- Create: `src/test/java/com/digitalbank/mfaservice/mfa/application/MfaChallengeServiceTest.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/ChallengeId.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/ChallengeStatus.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/ChallengeVerificationStatus.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/domain/Challenge.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/application/ChallengeResult.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/application/port/ChallengeStore.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/application/MfaChallengeService.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/adapter/memory/InMemoryChallengeStore.java`

**Interfaces:**
- `ChallengeStore.save(Challenge): void` and `find(ChallengeId): Optional<Challenge>`.
- `MfaChallengeService.create(EnrollmentId): ChallengeResult` and `verify(ChallengeId, String): ChallengeResult`.
- `Challenge.verify(Instant, BooleanSupplier): ChallengeVerificationStatus`.

- [x] **Step 1: Write failing challenge tests**

Test creation metadata and attempt budget, invalid code decrement, exhaustion on the final allowed failure, expired code rejection at the exact boundary and after it, successful consumption, replay rejection, unknown challenge rejection, and no verifier call for expired/consumed/exhausted challenges.

- [x] **Step 2: Run challenge tests to verify they fail for missing contracts**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=MfaChallengeServiceTest test`

Expected: compilation fails because challenge contracts do not exist yet.

- [x] **Step 3: Implement challenge state transitions**

Make `Challenge.verify` synchronized. Check terminal state first, then expiry using `!now.isBefore(expiresAt)`, then invoke the supplied verifier exactly once for an open challenge. Successful verification sets `CONSUMED`; failed verification increments attempts and sets `EXHAUSTED` at the limit.

- [x] **Step 4: Implement `MfaChallengeService` and the in-memory store**

Require an active enrollment on creation, calculate expiry with the injected clock and configured TTL, and pass the enrollment credential verifier into the synchronized challenge transition. Return remaining attempts and typed status, never credential data.

- [x] **Step 5: Run challenge tests and the full unit suite**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TotpEnrollmentServiceTest,MfaChallengeServiceTest test`

Expected: all focused tests pass with no verifier call after terminal transitions.

- [x] **Step 6: Commit the challenge slice**

```bash
git add src/main/java/com/digitalbank/mfaservice/mfa src/test/java/com/digitalbank/mfaservice/mfa/application/MfaChallengeServiceTest.java
git commit -m "feat: add bounded mfa challenge foundation"
```

### Task 4: Wire the standard provider, production randomness, and configuration

**Files:**
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/adapter/totp/SamStevensTotpProvider.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/adapter/random/SecureMfaIdentifierGenerator.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/config/MfaProperties.java`
- Create: `src/main/java/com/digitalbank/mfaservice/mfa/config/MfaConfiguration.java`
- Modify: `src/main/java/com/digitalbank/mfaservice/MfaServiceApplication.java`
- Modify: `src/main/resources/application.properties`
- Create: `src/test/java/com/digitalbank/mfaservice/mfa/adapter/totp/SamStevensTotpProviderTest.java`

**Interfaces:**
- `SamStevensTotpProvider` implements `TotpProvider` using the library's `DefaultSecretGenerator`, `DefaultCodeVerifier`, and a clock-backed `TimeProvider`.
- `MfaProperties` binds `mfa.challenge.ttl` and `mfa.challenge.max-attempts` with positive/upper-bound validation.

- [x] **Step 1: Write the failing provider test**

Use the fixed RFC 6238 test secret and clock instant to assert that the standard provider accepts the known valid code, rejects an invalid code, and generates a nonblank Base32 secret. The test should fail until the adapter exists.

- [x] **Step 2: Implement the standard library adapter**

Delegate all TOTP calculations to `dev.samstevens.totp`. Adapt `Clock.instant()` to the library `TimeProvider`; do not implement HMAC, truncation, or time-step arithmetic in repository code.

- [x] **Step 3: Implement secure identifier generation and Spring configuration**

Use secure random UUID values for enrollment/challenge ids. Register the clock, identifier generator, provider, stores, properties, and application services as beans. Set the documented local defaults in `application.properties`.

- [x] **Step 4: Run provider and application tests**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=SamStevensTotpProviderTest,MfaServiceApplicationIT test`

Expected: the provider test and existing bootstrap integration test pass.

- [x] **Step 5: Commit provider/configuration wiring**

```bash
git add pom.xml src/main/java src/main/resources/application.properties src/test/java/com/digitalbank/mfaservice/mfa/adapter/totp
git commit -m "feat: wire standard totp provider"
```

### Task 5: Update OpenAPI metadata and operational documentation

**Files:**
- Modify: `src/main/java/com/digitalbank/mfaservice/MfaServiceApplication.java`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Create: `docs/problem-details.md`

- [x] **Step 1: Update metadata and docs**

State that the current release contains Java application ports and in-memory adapters only, describe secret non-disclosure and terminal challenge behavior, document defaults and deterministic test seams, and list auth-service/persistence/provider/gateway integration as deferred. Explain that no HTTP routes or Insomnia requests exist in this wave.

- [x] **Step 2: Run formatting and documentation checks**

Run: `./mvnw --batch-mode --no-transfer-progress spotless:check` and `git diff --check`.

Expected: both commands exit 0.

- [x] **Step 3: Commit documentation**

```bash
git add README.md AGENTS.md docs/problem-details.md src/main/java/com/digitalbank/mfaservice/MfaServiceApplication.java
git commit -m "docs: document mfa foundation boundaries"
```

### Task 6: Verify, review, push, and open the stacked PR

**Files:**
- No source changes expected; inspect the complete branch diff.

- [x] **Step 1: Run the required verification commands**

Run each command from the repository root:

```bash
./mvnw --batch-mode --no-transfer-progress verify
helm lint helm --strict --values helm/values-sit.yaml
helm template mfa-service helm --namespace digital-bank-sit --values helm/values-sit.yaml > /tmp/mfa-service-rendered.yaml
docker build -t digital-bank-java/mfa-service:foundation .
git diff --check feature/49-mfa-service-bootstrap...HEAD
```

For Docker smoke, start the built image with the same disposable Config Server pattern used by `.github/workflows/ci.yml`, verify the image user is `10001:10001`, and probe health, liveness, and readiness. Record command results in the PR body.

- [x] **Step 2: Review the final diff and branch ancestry**

Run `git diff --stat feature/49-mfa-service-bootstrap...HEAD`, `git log --oneline --decorate feature/49-mfa-service-bootstrap..HEAD`, and confirm the branch base is `021c48e` with no unrelated files or secrets.

- [x] **Step 3: Push the feature branch**

```bash
git push --set-upstream origin feature/49-mfa-provider-foundation
```

- [x] **Step 4: Open a non-draft PR against the bootstrap branch**

Use base `feature/49-mfa-service-bootstrap`, title `feat: add mfa provider foundation`, and a valid Markdown body linking [#49](https://github.com/digital-bank-java/.github/issues/49), [#50](https://github.com/digital-bank-java/.github/issues/50), [#51](https://github.com/digital-bank-java/.github/issues/51), and [bootstrap PR #1](https://github.com/digital-bank-java/mfa-service/pull/1). Explain that PR #1 must merge first, this PR depends on its runtime/build foundation, and neither PR is merged by the coding agent. Explicitly state that the new PR is non-draft and that auth-service, persistence, provider integrations, and routes remain later work.

- [x] **Step 5: Verify PR state**

Run `gh pr view --json url,isDraft,baseRefName,headRefName,state` and confirm the URL, `isDraft=false`, base `feature/49-mfa-service-bootstrap`, and `state=OPEN`. Do not merge.
