create table mfa_enrollments (
    id varchar(128) not null,
    subject_id varchar(255) not null,
    created_at timestamp with time zone not null,
    totp_secret_ciphertext text not null,
    status varchar(16) not null,
    constraint pk_mfa_enrollments primary key (id),
    constraint ck_mfa_enrollments_status check (status in ('PENDING', 'ACTIVE'))
);

create index idx_mfa_enrollments_subject_id on mfa_enrollments (subject_id);

create table mfa_challenges (
    id varchar(128) not null,
    enrollment_id varchar(128) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    max_attempts smallint not null,
    failed_attempts smallint not null,
    status varchar(16) not null,
    constraint pk_mfa_challenges primary key (id),
    constraint fk_mfa_challenges_enrollment_id
        foreign key (enrollment_id) references mfa_enrollments (id),
    constraint ck_mfa_challenges_attempts check (max_attempts between 1 and 10),
    constraint ck_mfa_challenges_failed_attempts check (failed_attempts between 0 and max_attempts),
    constraint ck_mfa_challenges_expiry check (expires_at > created_at),
    constraint ck_mfa_challenges_status check (status in ('OPEN', 'CONSUMED', 'EXHAUSTED', 'EXPIRED'))
);

create index idx_mfa_challenges_enrollment_id on mfa_challenges (enrollment_id);
