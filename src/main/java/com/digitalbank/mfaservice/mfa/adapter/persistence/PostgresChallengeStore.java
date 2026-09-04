package com.digitalbank.mfaservice.mfa.adapter.persistence;

import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.domain.Challenge;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import com.digitalbank.mfaservice.mfa.domain.ChallengeStatus;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresChallengeStore implements ChallengeStore {

    private static final String FIND_SQL = """
            select id, enrollment_id, created_at, expires_at, max_attempts, failed_attempts, status
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
                set enrollment_id = ?, created_at = ?, expires_at = ?, max_attempts = ?, failed_attempts = ?, status = ?
                where id = ?
                """,
                challenge.enrollmentId().value(),
                challenge.createdAt(),
                challenge.expiresAt(),
                challenge.maxAttempts(),
                challenge.failedAttempts(),
                challenge.status().name(),
                challenge.id().value());
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    insert into mfa_challenges
                        (id, enrollment_id, created_at, expires_at, max_attempts, failed_attempts, status)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    challenge.id().value(),
                    challenge.enrollmentId().value(),
                    challenge.createdAt(),
                    challenge.expiresAt(),
                    challenge.maxAttempts(),
                    challenge.failedAttempts(),
                    challenge.status().name());
        }
    }

    @Override
    public Optional<Challenge> find(ChallengeId challengeId) {
        return jdbcTemplate.query(FIND_SQL, this::map, challengeId.value()).stream()
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
                ChallengeStatus.valueOf(resultSet.getString("status")));
    }
}
