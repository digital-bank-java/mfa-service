create table mfa_assurance_outbox (
    event_id varchar(128) not null,
    topic varchar(255) not null,
    event_type varchar(128) not null,
    aggregate_id varchar(128) not null,
    payload text not null,
    created_at timestamp with time zone not null,
    correlation_id varchar(255) not null,
    causation_id varchar(255) not null,
    producer varchar(128) not null,
    schema_version varchar(32) not null,
    occurred_at timestamp with time zone not null,
    attempts integer not null default 0,
    last_attempt_at timestamp with time zone,
    last_error text,
    next_attempt_at timestamp with time zone,
    published_at timestamp with time zone,
    constraint pk_mfa_assurance_outbox primary key (event_id)
);

create index idx_mfa_assurance_outbox_pending
    on mfa_assurance_outbox (published_at, next_attempt_at, created_at);
