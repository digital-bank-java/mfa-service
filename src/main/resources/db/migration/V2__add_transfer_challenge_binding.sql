alter table mfa_challenges add column transfer_id varchar(128);
alter table mfa_challenges add column decision_id varchar(128);
alter table mfa_challenges add column bound_subject_id varchar(255);
alter table mfa_challenges add column source_account_id varchar(128);
alter table mfa_challenges add column destination_account_id varchar(128);
alter table mfa_challenges add column amount numeric(19, 4);
alter table mfa_challenges add column currency varchar(3);
alter table mfa_challenges add column policy_version varchar(128);
alter table mfa_challenges add column correlation_id varchar(128);

alter table mfa_challenges
    add constraint uq_mfa_challenges_decision_id unique (decision_id);

alter table mfa_challenges
    add constraint ck_mfa_challenges_transfer_binding check (
        (transfer_id is null and decision_id is null and bound_subject_id is null
            and source_account_id is null and destination_account_id is null and amount is null
            and currency is null and policy_version is null and correlation_id is null)
        or (transfer_id is not null and decision_id is not null and bound_subject_id is not null
            and source_account_id is not null and destination_account_id is not null and amount is not null
            and amount > 0 and currency is not null and policy_version is not null and correlation_id is not null)
    );
