# MFA Persistence

The service persists enrollments and challenges in PostgreSQL. Flyway migration `V1__create_mfa_persistence.sql` creates the `mfa_enrollments` and `mfa_challenges` tables, including lifecycle, attempt-budget, ownership, expiry, and foreign-key constraints. Migration `V2__add_transfer_challenge_binding.sql` adds the immutable transfer decision binding and a uniqueness constraint for each risk decision.

The application services keep the existing `EnrollmentStore` and `ChallengeStore` ports. Runtime wiring supplies JDBC adapters and a Spring transaction runner. Reads used by state-changing workflows use `SELECT ... FOR UPDATE`; the domain transition and update occur in the same transaction. This makes enrollment activation one-time and keeps challenge expiry, failed-attempt increments, exhaustion, consumption, and replay outcomes consistent across service instances.

TOTP secrets are encrypted before insertion or update with AES-GCM. Each ciphertext includes a fresh random 12-byte IV and the authenticated encryption tag. The key is a base64-encoded 32-byte AES key supplied through `MFA_TOTP_ENCRYPTION_KEY` or the equivalent `mfa.totp.encryption-key` property. The key is never stored with the ciphertext, committed to Git, returned by an API, or written to logs.

Transfer-bound challenge rows retain the transfer id, decision id, authenticated subject, source and destination account references, normalized amount and currency, policy version, and correlation id. These fields are written as one binding and are checked as a unit on verification; the original transfer intent is never changed by MFA verification.

SIT uses the shared PostgreSQL service with a dedicated `mfa_service` database. The database credential Secret and the encryption-key Secret are deployment inputs; this repository contains only Secret names and keys. UAT and production must provide equivalent external secret management before enabling the service.
