package com.digitalbank.mfaservice.mfa.adapter.persistence;

import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.domain.Challenge;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.ChallengeStatus;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.TransferChallengeBinding;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresChallengeStore implements ChallengeStore {

    private static final String FIND_SQL = """
            select id, enrollment_id, created_at, expires_at, max_attempts, failed_attempts, status,
                   transfer_id, reservation_request_id, decision_id, decision_request_id, customer_id,
                   source_account_id, destination_account_id, amount, currency, policy_version, correlation_id
            from mfa_challenges
            where id = ?
            for update
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresChallengeStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Challenge challenge) {
        var updated = jdbcTemplate.update(
                """
                update mfa_challenges
                set enrollment_id = ?, created_at = ?, expires_at = ?, max_attempts = ?, failed_attempts = ?, status = ?,
                    transfer_id = ?, reservation_request_id = ?, decision_id = ?, decision_request_id = ?, customer_id = ?,
                    source_account_id = ?,
                    destination_account_id = ?, amount = ?, currency = ?, policy_version = ?, correlation_id = ?
                where id = ?
                """,
                challenge.enrollmentId().value(),
                Timestamp.from(challenge.createdAt()),
                Timestamp.from(challenge.expiresAt()),
                challenge.maxAttempts(),
                challenge.failedAttempts(),
                challenge.status().name(),
                transferValue(challenge, "transferId"),
                transferValue(challenge, "reservationRequestId"),
                transferValue(challenge, "decisionId"),
                transferValue(challenge, "decisionRequestId"),
                transferValue(challenge, "customerId"),
                transferValue(challenge, "sourceAccountId"),
                transferValue(challenge, "destinationAccountId"),
                transferValue(challenge, "amount"),
                transferValue(challenge, "currency"),
                transferValue(challenge, "policyVersion"),
                transferValue(challenge, "correlationId"),
                challenge.id().value());
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    insert into mfa_challenges
                        (id, enrollment_id, created_at, expires_at, max_attempts, failed_attempts, status,
                         transfer_id, reservation_request_id, decision_id, decision_request_id, customer_id,
                         source_account_id, destination_account_id,
                         amount, currency, policy_version, correlation_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    challenge.id().value(),
                    challenge.enrollmentId().value(),
                    Timestamp.from(challenge.createdAt()),
                    Timestamp.from(challenge.expiresAt()),
                    challenge.maxAttempts(),
                    challenge.failedAttempts(),
                    challenge.status().name(),
                    transferValue(challenge, "transferId"),
                    transferValue(challenge, "reservationRequestId"),
                    transferValue(challenge, "decisionId"),
                    transferValue(challenge, "decisionRequestId"),
                    transferValue(challenge, "customerId"),
                    transferValue(challenge, "sourceAccountId"),
                    transferValue(challenge, "destinationAccountId"),
                    transferValue(challenge, "amount"),
                    transferValue(challenge, "currency"),
                    transferValue(challenge, "policyVersion"),
                    transferValue(challenge, "correlationId"));
        }
    }

    @Override
    public Optional<Challenge> find(ChallengeId challengeId) {
        return jdbcTemplate.query(FIND_SQL, this::map, challengeId.value()).stream()
                .findFirst();
    }

    @Override
    public Optional<Challenge> findByDecisionId(String decisionId) {
        return jdbcTemplate
                .query(FIND_SQL.replace("where id = ?", "where decision_id = ?"), this::map, decisionId)
                .stream()
                .findFirst();
    }

    private Challenge map(ResultSet resultSet, int rowNumber) throws SQLException {
        return Challenge.restore(
                new ChallengeId(resultSet.getString("id")),
                new EnrollmentId(resultSet.getString("enrollment_id")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getInt("max_attempts"),
                resultSet.getInt("failed_attempts"),
                ChallengeStatus.valueOf(resultSet.getString("status")),
                transferBinding(resultSet));
    }

    private static TransferChallengeBinding transferBinding(ResultSet resultSet) throws SQLException {
        var transferId = resultSet.getString("transfer_id");
        if (transferId == null) {
            return null;
        }
        return new TransferChallengeBinding(
                transferId,
                resultSet.getString("reservation_request_id"),
                resultSet.getString("decision_id"),
                resultSet.getString("decision_request_id"),
                resultSet.getString("customer_id"),
                resultSet.getString("source_account_id"),
                resultSet.getString("destination_account_id"),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("currency"),
                resultSet.getString("policy_version"),
                resultSet.getString("correlation_id"));
    }

    private static Object transferValue(Challenge challenge, String field) {
        var binding = challenge.transferBinding();
        if (binding == null) {
            return null;
        }
        return switch (field) {
            case "transferId" -> binding.transferId();
            case "reservationRequestId" -> binding.reservationRequestId();
            case "decisionId" -> binding.decisionId();
            case "decisionRequestId" -> binding.decisionRequestId();
            case "customerId" -> binding.customerId();
            case "sourceAccountId" -> binding.sourceAccountId();
            case "destinationAccountId" -> binding.destinationAccountId();
            case "amount" -> binding.amount();
            case "currency" -> binding.currency();
            case "policyVersion" -> binding.policyVersion();
            case "correlationId" -> binding.correlationId();
            default -> throw new IllegalArgumentException("Unknown transfer binding field: " + field);
        };
    }
}
