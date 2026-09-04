package com.digitalbank.mfaservice.mfa.adapter.persistence;

import com.digitalbank.mfaservice.mfa.application.port.EnrollmentStore;
import com.digitalbank.mfaservice.mfa.domain.Enrollment;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentId;
import com.digitalbank.mfaservice.mfa.domain.EnrollmentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresEnrollmentStore implements EnrollmentStore {

    private static final String FIND_SQL = """
            select id, subject_id, created_at, totp_secret_ciphertext, status
            from mfa_enrollments
            where id = ?
            for update
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TotpSecretProtector secretProtector;

    public PostgresEnrollmentStore(JdbcTemplate jdbcTemplate, TotpSecretProtector secretProtector) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretProtector = secretProtector;
    }

    @Override
    public void save(Enrollment enrollment) {
        var ciphertext = enrollment.mapSecret(secretProtector::encrypt);
        var updated = jdbcTemplate.update(
                """
                update mfa_enrollments
                set subject_id = ?, created_at = ?, totp_secret_ciphertext = ?, status = ?
                where id = ?
                """,
                enrollment.subjectId(),
                enrollment.createdAt(),
                ciphertext,
                enrollment.status().name(),
                enrollment.id().value());
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    insert into mfa_enrollments (id, subject_id, created_at, totp_secret_ciphertext, status)
                    values (?, ?, ?, ?, ?)
                    """,
                    enrollment.id().value(),
                    enrollment.subjectId(),
                    enrollment.createdAt(),
                    ciphertext,
                    enrollment.status().name());
        }
    }

    @Override
    public Optional<Enrollment> find(EnrollmentId enrollmentId) {
        return jdbcTemplate.query(FIND_SQL, this::map, enrollmentId.value()).stream()
                .findFirst();
    }

    private Enrollment map(ResultSet resultSet, int rowNumber) throws SQLException {
        return Enrollment.restore(
                new EnrollmentId(resultSet.getString("id")),
                resultSet.getString("subject_id"),
                secretProtector.decrypt(resultSet.getString("totp_secret_ciphertext")),
                resultSet.getTimestamp("created_at").toInstant(),
                EnrollmentStatus.valueOf(resultSet.getString("status")));
    }
}
