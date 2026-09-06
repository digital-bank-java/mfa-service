package com.digitalbank.mfaservice.mfa.adapter.persistence;

import com.digitalbank.mfaservice.mfa.application.MfaAssuranceEventHeaders;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceGrantedEvent;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceOutboxEntry;
import com.digitalbank.mfaservice.mfa.application.MfaAssuranceOutboxStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresMfaAssuranceOutboxStore implements MfaAssuranceOutboxStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresMfaAssuranceOutboxStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(MfaAssuranceGrantedEvent event) {
        jdbcTemplate.update(
                """
                insert into mfa_assurance_outbox
                    (event_id, topic, event_type, aggregate_id, payload, created_at,
                     correlation_id, causation_id, producer, schema_version, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.eventId(),
                MfaAssuranceGrantedEvent.TOPIC,
                event.eventType(),
                event.aggregateId(),
                event.toJson(objectMapper),
                Timestamp.from(event.occurredAt()),
                event.correlationId(),
                event.causationId(),
                event.producer(),
                event.schemaVersion(),
                Timestamp.from(event.occurredAt()));
    }

    @Override
    public List<MfaAssuranceOutboxEntry> findPending(int limit, Instant at) {
        return jdbcTemplate.query("""
                select event_id, topic, aggregate_id, payload, created_at, attempts, published_at,
                       correlation_id, causation_id, producer, schema_version, occurred_at
                from mfa_assurance_outbox
                where published_at is null
                  and (next_attempt_at is null or next_attempt_at <= ?)
                order by created_at, event_id
                limit ?
                """, this::map, Timestamp.from(at), limit);
    }

    @Override
    public void markPublished(String eventId, Instant publishedAt) {
        jdbcTemplate.update(
                "update mfa_assurance_outbox set published_at = ? where event_id = ? and published_at is null",
                Timestamp.from(publishedAt),
                eventId);
    }

    @Override
    public void recordFailure(String eventId, Instant attemptedAt, String error) {
        jdbcTemplate.update(
                """
                update mfa_assurance_outbox
                set attempts = attempts + 1, last_attempt_at = ?, last_error = ?, next_attempt_at = ?
                where event_id = ? and published_at is null
                """, Timestamp.from(attemptedAt), error, Timestamp.from(attemptedAt.plusSeconds(5)), eventId);
    }

    private MfaAssuranceOutboxEntry map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MfaAssuranceOutboxEntry(
                resultSet.getString("event_id"),
                resultSet.getString("topic"),
                resultSet.getString("aggregate_id"),
                resultSet.getString("payload"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getInt("attempts"),
                resultSet.getTimestamp("published_at") == null
                        ? null
                        : resultSet.getTimestamp("published_at").toInstant(),
                new MfaAssuranceEventHeaders(
                        resultSet.getString("event_id"),
                        resultSet.getString("correlation_id"),
                        resultSet.getString("causation_id"),
                        resultSet.getString("producer"),
                        resultSet.getString("schema_version"),
                        resultSet.getTimestamp("occurred_at").toInstant()));
    }
}
