-- POC-04 Phase 1: authoritative foundation and direct table grants only.
-- The admin-only security-role bootstrap must precede this migration.
-- V5/V6 capability functions and their executor table privileges are deferred.
DO $$
BEGIN
    IF current_role <> 'vra_owner' THEN
        RAISE EXCEPTION 'V4 requires the accepted migration owner role';
    END IF;
END
$$;

CREATE TABLE vra.account (
    account_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    auth_generation BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.statement_timestamp(),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.statement_timestamp(),
    CONSTRAINT account_pk PRIMARY KEY (account_id),
    CONSTRAINT account_state_ck CHECK (state IN ('ACTIVE', 'LOCKED', 'DISABLED', 'CLOSED')),
    CONSTRAINT account_auth_generation_ck CHECK (auth_generation >= 0),
    CONSTRAINT account_version_ck CHECK (version >= 0),
    CONSTRAINT account_time_ck CHECK (
        pg_catalog.isfinite(created_at) AND pg_catalog.isfinite(changed_at) AND changed_at >= created_at
    )
);

CREATE TABLE vra.external_identity_binding (
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    account_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.statement_timestamp(),
    CONSTRAINT external_identity_binding_pk PRIMARY KEY (issuer, subject),
    CONSTRAINT external_identity_binding_issuer_ck CHECK (pg_catalog.btrim(issuer) <> ''),
    CONSTRAINT external_identity_binding_subject_ck CHECK (pg_catalog.btrim(subject) <> ''),
    CONSTRAINT external_identity_binding_time_ck CHECK (pg_catalog.isfinite(created_at)),
    CONSTRAINT external_identity_binding_account_fk FOREIGN KEY (account_id)
        REFERENCES vra.account (account_id)
);
-- An account may have multiple distinct validated external identity tuples.
CREATE INDEX external_identity_binding_account_idx ON vra.external_identity_binding (account_id);

CREATE TABLE vra.organization (
    org_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT organization_pk PRIMARY KEY (org_id),
    CONSTRAINT organization_state_ck CHECK (state IN ('ACTIVE', 'CLOSED'))
);

CREATE TABLE vra.organization_membership (
    account_id UUID NOT NULL,
    org_id UUID NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT organization_membership_pk PRIMARY KEY (account_id, org_id),
    CONSTRAINT organization_membership_state_ck CHECK (state IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT organization_membership_version_ck CHECK (version >= 0),
    CONSTRAINT organization_membership_account_fk FOREIGN KEY (account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT organization_membership_org_fk FOREIGN KEY (org_id)
        REFERENCES vra.organization (org_id)
);
CREATE INDEX organization_membership_scope_idx ON vra.organization_membership (org_id, state, account_id);

CREATE TABLE vra.inventory_operation_grant (
    account_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    operation VARCHAR(64) NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT inventory_operation_grant_pk PRIMARY KEY (account_id, owner_id, operation),
    CONSTRAINT inventory_operation_grant_operation_ck CHECK (operation = 'RESERVE'),
    CONSTRAINT inventory_operation_grant_state_ck CHECK (state IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT inventory_operation_grant_version_ck CHECK (version >= 0),
    CONSTRAINT inventory_operation_grant_account_fk FOREIGN KEY (account_id)
        REFERENCES vra.account (account_id)
);
-- Inventory owner UUIDs are separate from organization/seller authority.
CREATE INDEX inventory_operation_grant_owner_idx ON vra.inventory_operation_grant (owner_id, operation, state, account_id);

CREATE TABLE vra.poc_account_record (
    record_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    owner_account_id UUID NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    label VARCHAR(80) NOT NULL,
    CONSTRAINT poc_account_record_pk PRIMARY KEY (record_id),
    CONSTRAINT poc_account_record_owner_fk FOREIGN KEY (owner_account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT poc_account_record_state_ck CHECK (state IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT poc_account_record_version_ck CHECK (version >= 0)
);
CREATE INDEX poc_account_record_owner_idx ON vra.poc_account_record (owner_account_id, state, record_id);

CREATE TABLE vra.poc_organization_record (
    record_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    owner_org_id UUID NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    label VARCHAR(80) NOT NULL,
    CONSTRAINT poc_organization_record_pk PRIMARY KEY (record_id),
    CONSTRAINT poc_organization_record_owner_fk FOREIGN KEY (owner_org_id)
        REFERENCES vra.organization (org_id),
    CONSTRAINT poc_organization_record_state_ck CHECK (state IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT poc_organization_record_version_ck CHECK (version >= 0)
);
CREATE INDEX poc_organization_record_owner_idx ON vra.poc_organization_record (owner_org_id, state, record_id);

CREATE TABLE vra.browser_session (
    session_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    verifier BYTEA NOT NULL,
    account_id UUID NOT NULL,
    generation BIGINT NOT NULL DEFAULT 0,
    account_auth_generation BIGINT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    idle_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    assurance_level VARCHAR(64) NOT NULL DEFAULT 'ORDINARY',
    assurance_expires_at TIMESTAMPTZ,
    csrf_token BYTEA NOT NULL,
    csrf_generation BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT browser_session_pk PRIMARY KEY (session_id),
    CONSTRAINT browser_session_verifier_uq UNIQUE (verifier),
    CONSTRAINT browser_session_actor_generation_uq UNIQUE (session_id, account_id, generation),
    CONSTRAINT browser_session_account_fk FOREIGN KEY (account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT browser_session_verifier_ck CHECK (pg_catalog.octet_length(verifier) = 32),
    CONSTRAINT browser_session_generation_ck CHECK (generation >= 0),
    CONSTRAINT browser_session_account_generation_ck CHECK (account_auth_generation >= 0),
    CONSTRAINT browser_session_csrf_ck CHECK (pg_catalog.octet_length(csrf_token) = 32),
    CONSTRAINT browser_session_csrf_generation_ck CHECK (csrf_generation >= 0 AND csrf_generation = generation),
    CONSTRAINT browser_session_time_ck CHECK (
        pg_catalog.isfinite(issued_at) AND pg_catalog.isfinite(last_used_at)
        AND pg_catalog.isfinite(idle_expires_at) AND pg_catalog.isfinite(absolute_expires_at)
        AND last_used_at >= issued_at AND last_used_at < absolute_expires_at
        AND absolute_expires_at > issued_at
        AND absolute_expires_at <= issued_at + INTERVAL '2 hours'
        AND idle_expires_at > last_used_at
        AND idle_expires_at <= last_used_at + INTERVAL '20 minutes'
        AND idle_expires_at <= absolute_expires_at
        AND (revoked_at IS NULL OR (pg_catalog.isfinite(revoked_at) AND revoked_at >= issued_at))
    ),
    CONSTRAINT browser_session_assurance_ck CHECK (
        (assurance_level = 'ORDINARY' AND assurance_expires_at IS NULL)
        OR (assurance_level = 'MFA' AND assurance_expires_at IS NOT NULL
            AND pg_catalog.isfinite(assurance_expires_at)
            AND assurance_expires_at > issued_at
            AND assurance_expires_at <= issued_at + INTERVAL '15 minutes'
            AND assurance_expires_at <= absolute_expires_at)
        OR (assurance_level = 'STEP_UP' AND assurance_expires_at IS NOT NULL
            AND pg_catalog.isfinite(assurance_expires_at)
            AND assurance_expires_at > issued_at
            AND assurance_expires_at <= issued_at + INTERVAL '2 minutes'
            AND assurance_expires_at <= absolute_expires_at)
    )
);
CREATE INDEX browser_session_account_idx ON vra.browser_session (account_id, revoked_at, absolute_expires_at);

CREATE TABLE vra.factor_challenge (
    challenge_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    actor_account_id UUID NOT NULL,
    session_id UUID NOT NULL,
    session_generation BIGINT NOT NULL,
    operation VARCHAR(64) NOT NULL,
    context_digest BYTEA NOT NULL,
    nonce_digest BYTEA NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT factor_challenge_pk PRIMARY KEY (challenge_id),
    CONSTRAINT factor_challenge_binding_uq UNIQUE (
        challenge_id, actor_account_id, session_id, session_generation, operation, context_digest
    ),
    CONSTRAINT factor_challenge_actor_context_uq UNIQUE (challenge_id, actor_account_id, context_digest),
    CONSTRAINT factor_challenge_account_fk FOREIGN KEY (actor_account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT factor_challenge_session_fk FOREIGN KEY (session_id, actor_account_id, session_generation)
        REFERENCES vra.browser_session (session_id, account_id, generation),
    CONSTRAINT factor_challenge_generation_ck CHECK (session_generation >= 0),
    CONSTRAINT factor_challenge_operation_ck CHECK (operation IN (
        'MAKER_AUTH', 'STAFF_SECURITY_GRANT', 'STAFF_SECURITY_REVOKE', 'TEST_ACCOUNT_STATE_TRANSITION'
    )),
    CONSTRAINT factor_challenge_context_ck CHECK (pg_catalog.octet_length(context_digest) = 32),
    CONSTRAINT factor_challenge_nonce_ck CHECK (pg_catalog.octet_length(nonce_digest) = 32),
    CONSTRAINT factor_challenge_time_ck CHECK (
        pg_catalog.isfinite(issued_at) AND pg_catalog.isfinite(expires_at)
        AND expires_at > issued_at AND expires_at <= issued_at + INTERVAL '90 seconds'
        AND (consumed_at IS NULL OR (consumed_at >= issued_at AND consumed_at < expires_at))
    )
);
CREATE INDEX factor_challenge_session_idx ON vra.factor_challenge (session_id, session_generation, operation);

CREATE TABLE vra.factor_assertion_use (
    jti VARCHAR(255) NOT NULL,
    challenge_id UUID NOT NULL,
    actor_account_id UUID NOT NULL,
    session_id UUID NOT NULL,
    session_generation BIGINT NOT NULL,
    operation VARCHAR(64) NOT NULL,
    context_digest BYTEA NOT NULL,
    jws_digest BYTEA NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ATTESTED',
    attested_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT factor_assertion_use_pk PRIMARY KEY (jti),
    CONSTRAINT factor_assertion_use_jti_ck CHECK (pg_catalog.btrim(jti) <> ''),
    CONSTRAINT factor_assertion_use_challenge_fk FOREIGN KEY (
        challenge_id, actor_account_id, session_id, session_generation, operation, context_digest
    ) REFERENCES vra.factor_challenge (
        challenge_id, actor_account_id, session_id, session_generation, operation, context_digest
    ),
    CONSTRAINT factor_assertion_use_generation_ck CHECK (session_generation >= 0),
    CONSTRAINT factor_assertion_use_operation_ck CHECK (operation IN (
        'MAKER_AUTH', 'STAFF_SECURITY_GRANT', 'STAFF_SECURITY_REVOKE', 'TEST_ACCOUNT_STATE_TRANSITION'
    )),
    CONSTRAINT factor_assertion_use_context_ck CHECK (pg_catalog.octet_length(context_digest) = 32),
    CONSTRAINT factor_assertion_use_jws_ck CHECK (pg_catalog.octet_length(jws_digest) = 32),
    CONSTRAINT factor_assertion_use_time_ck CHECK (
        pg_catalog.isfinite(attested_at) AND pg_catalog.isfinite(expires_at)
        AND expires_at > attested_at AND expires_at <= attested_at + INTERVAL '90 seconds'
    ),
    CONSTRAINT factor_assertion_use_state_ck CHECK (
        (state = 'ATTESTED' AND used_at IS NULL)
        OR (state = 'CONSUMED' AND used_at IS NOT NULL AND used_at >= attested_at AND used_at < expires_at)
    )
);
CREATE INDEX factor_assertion_use_challenge_idx ON vra.factor_assertion_use (challenge_id, state);

CREATE TABLE vra.staff_role_assignment (
    account_id UUID NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    scope_id UUID NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT staff_role_assignment_pk PRIMARY KEY (account_id, role_code, scope_id),
    CONSTRAINT staff_role_assignment_account_fk FOREIGN KEY (account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT staff_role_assignment_role_ck CHECK (role_code IN (
        'STAFF_SECURITY_GRANT_MAKER', 'STAFF_SECURITY_GRANT_CHECKER',
        'TEST_ACCOUNT_STATE_ADMIN', 'SECURITY_AUDIT_REVIEWER'
    )),
    CONSTRAINT staff_role_assignment_version_ck CHECK (version >= 0),
    CONSTRAINT staff_role_assignment_time_ck CHECK (pg_catalog.isfinite(granted_at)),
    CONSTRAINT staff_role_assignment_state_ck CHECK (
        (state = 'ACTIVE' AND revoked_at IS NULL)
        OR (state = 'REVOKED' AND revoked_at IS NOT NULL
            AND pg_catalog.isfinite(revoked_at) AND revoked_at >= granted_at)
    )
);
-- Every approved role is scoped; a NULL/global wildcard is not representable.
CREATE INDEX staff_role_assignment_scope_idx ON vra.staff_role_assignment (scope_id, role_code, state, account_id);

CREATE TABLE vra.security_role_proposal (
    proposal_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    maker_account_id UUID NOT NULL,
    maker_session_id UUID NOT NULL,
    maker_session_generation BIGINT NOT NULL,
    target_account_id UUID NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    scope_id UUID NOT NULL,
    reason VARCHAR(256) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    change_digest BYTEA NOT NULL,
    state VARCHAR(64) NOT NULL DEFAULT 'PROPOSED',
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT security_role_proposal_pk PRIMARY KEY (proposal_id),
    CONSTRAINT security_role_proposal_digest_version_uq UNIQUE (proposal_id, change_digest, version),
    CONSTRAINT security_role_proposal_maker_fk FOREIGN KEY (maker_account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT security_role_proposal_session_fk FOREIGN KEY (
        maker_session_id, maker_account_id, maker_session_generation
    ) REFERENCES vra.browser_session (session_id, account_id, generation),
    CONSTRAINT security_role_proposal_target_fk FOREIGN KEY (target_account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT security_role_proposal_generation_ck CHECK (maker_session_generation >= 0),
    CONSTRAINT security_role_proposal_role_ck CHECK (role_code = 'SECURITY_AUDIT_REVIEWER'),
    CONSTRAINT security_role_proposal_reason_ck CHECK (reason ~ '[^[:space:]]'),
    CONSTRAINT security_role_proposal_version_ck CHECK (version = 1),
    CONSTRAINT security_role_proposal_digest_ck CHECK (pg_catalog.octet_length(change_digest) = 32),
    CONSTRAINT security_role_proposal_state_ck CHECK (
        state IN ('PROPOSED', 'APPROVED', 'REJECTED', 'EXECUTED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT security_role_proposal_time_ck CHECK (
        pg_catalog.isfinite(created_at) AND pg_catalog.isfinite(expires_at)
        AND expires_at > created_at AND expires_at <= created_at + INTERVAL '15 minutes'
    )
);
-- Ordinary callers cannot update a proposal's authority tuple or digest.
-- Replacement is a new proposal ID/digest; V6 owns guarded state transitions.
CREATE INDEX security_role_proposal_maker_idx ON vra.security_role_proposal (maker_account_id, state, created_at);
CREATE INDEX security_role_proposal_target_idx ON vra.security_role_proposal (target_account_id, scope_id, state);

CREATE TABLE vra.security_role_approval (
    approval_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    proposal_id UUID NOT NULL,
    proposal_digest BYTEA NOT NULL,
    proposal_version BIGINT NOT NULL,
    checker_account_id UUID NOT NULL,
    checker_session_id UUID NOT NULL,
    checker_session_generation BIGINT NOT NULL,
    challenge_id UUID NOT NULL,
    context_digest BYTEA NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT security_role_approval_pk PRIMARY KEY (approval_id),
    CONSTRAINT security_role_approval_proposal_uq UNIQUE (proposal_id),
    CONSTRAINT security_role_approval_link_uq UNIQUE (approval_id, proposal_id),
    CONSTRAINT security_role_approval_proposal_fk FOREIGN KEY (proposal_id, proposal_digest, proposal_version)
        REFERENCES vra.security_role_proposal (proposal_id, change_digest, version),
    CONSTRAINT security_role_approval_checker_fk FOREIGN KEY (checker_account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT security_role_approval_session_fk FOREIGN KEY (
        checker_session_id, checker_account_id, checker_session_generation
    ) REFERENCES vra.browser_session (session_id, account_id, generation),
    CONSTRAINT security_role_approval_challenge_fk FOREIGN KEY (challenge_id, checker_account_id, context_digest)
        REFERENCES vra.factor_challenge (challenge_id, actor_account_id, context_digest),
    CONSTRAINT security_role_approval_version_ck CHECK (proposal_version = 1),
    CONSTRAINT security_role_approval_generation_ck CHECK (checker_session_generation >= 0),
    CONSTRAINT security_role_approval_digest_ck CHECK (pg_catalog.octet_length(proposal_digest) = 32),
    CONSTRAINT security_role_approval_context_ck CHECK (
        pg_catalog.octet_length(context_digest) = 32 AND context_digest = proposal_digest
    ),
    CONSTRAINT security_role_approval_time_ck CHECK (
        pg_catalog.isfinite(approved_at) AND pg_catalog.isfinite(expires_at)
        AND expires_at > approved_at AND expires_at <= approved_at + INTERVAL '5 minutes'
        AND (consumed_at IS NULL OR (consumed_at >= approved_at AND consumed_at < expires_at))
    )
);
CREATE INDEX security_role_approval_checker_idx ON vra.security_role_approval (checker_account_id, checker_session_id);

CREATE TABLE vra.protected_security_audit (
    audit_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    actor_type VARCHAR(64) NOT NULL,
    actor_account_id UUID,
    actor_workload VARCHAR(64),
    subject_account_id UUID,
    action_code VARCHAR(64) NOT NULL,
    result_code VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id UUID,
    target_reference VARCHAR(80),
    reason VARCHAR(256) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.statement_timestamp(),
    request_id UUID NOT NULL,
    proposal_id UUID,
    approval_id UUID,
    delta_code VARCHAR(64) NOT NULL,
    subject_auth_generation_before BIGINT,
    subject_auth_generation_after BIGINT,
    CONSTRAINT protected_security_audit_pk PRIMARY KEY (audit_id),
    CONSTRAINT protected_security_audit_actor_fk FOREIGN KEY (actor_account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT protected_security_audit_subject_fk FOREIGN KEY (subject_account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT protected_security_audit_proposal_fk FOREIGN KEY (proposal_id)
        REFERENCES vra.security_role_proposal (proposal_id),
    CONSTRAINT protected_security_audit_approval_fk FOREIGN KEY (approval_id)
        REFERENCES vra.security_role_approval (approval_id),
    CONSTRAINT protected_security_audit_approval_link_fk FOREIGN KEY (approval_id, proposal_id)
        REFERENCES vra.security_role_approval (approval_id, proposal_id),
    CONSTRAINT protected_security_audit_actor_ck CHECK (
        (actor_type = 'HUMAN' AND actor_account_id IS NOT NULL AND actor_workload IS NULL)
        OR (actor_type = 'WORKLOAD' AND actor_account_id IS NULL AND actor_workload IS NOT NULL
            AND actor_workload = 'vra_audit_evidence_reader')
    ),
    CONSTRAINT protected_security_audit_subject_ck CHECK (
        subject_account_id IS NULL OR actor_account_id IS NULL OR subject_account_id <> actor_account_id
    ),
    CONSTRAINT protected_security_audit_action_ck CHECK (action_code IN (
        'STAFF_GRANT_PROPOSED', 'STAFF_GRANT_APPROVED', 'STAFF_GRANT_REJECTED',
        'STAFF_GRANT_EXECUTED', 'STAFF_GRANT_REVOKED', 'ACCOUNT_STATE_TRANSITION',
        'STAFF_GRANT_DENIED', 'AUDIT_TAMPER_ATTEMPT', 'AUDIT_EXPORT'
    )),
    CONSTRAINT protected_security_audit_result_ck CHECK (result_code IN ('SUCCEEDED', 'DENIED')),
    CONSTRAINT protected_security_audit_target_ck CHECK (target_type IN (
        'ACCOUNT', 'STAFF_ROLE', 'ROLE_PROPOSAL', 'ROLE_APPROVAL', 'PROTECTED_AUDIT', 'AUDIT_EVIDENCE'
    )),
    CONSTRAINT protected_security_audit_reference_ck CHECK (
        target_reference IS NULL OR pg_catalog.btrim(target_reference) <> ''
    ),
    CONSTRAINT protected_security_audit_reason_ck CHECK (reason ~ '[^[:space:]]'),
    CONSTRAINT protected_security_audit_time_ck CHECK (pg_catalog.isfinite(occurred_at)),
    CONSTRAINT protected_security_audit_delta_ck CHECK (delta_code IN (
        'NONE', 'ROLE_GRANTED', 'ROLE_REVOKED', 'ACCOUNT_LOCKED', 'ACCOUNT_DISABLED', 'ACCOUNT_CLOSED'
    )),
    CONSTRAINT protected_security_audit_generation_ck CHECK (
        (subject_auth_generation_before IS NULL AND subject_auth_generation_after IS NULL AND delta_code = 'NONE')
        OR (subject_auth_generation_before IS NOT NULL AND subject_auth_generation_before >= 0
            AND subject_auth_generation_after IS NOT NULL AND subject_auth_generation_after >= 0
            AND subject_auth_generation_after - subject_auth_generation_before = 1
            AND delta_code IN ('ROLE_GRANTED', 'ROLE_REVOKED', 'ACCOUNT_LOCKED', 'ACCOUNT_DISABLED', 'ACCOUNT_CLOSED'))
    ),
    CONSTRAINT protected_security_audit_approval_shape_ck CHECK (approval_id IS NULL OR proposal_id IS NOT NULL),
    CONSTRAINT protected_security_audit_export_shape_ck CHECK (
        (actor_type = 'WORKLOAD' AND action_code = 'AUDIT_EXPORT' AND target_type = 'AUDIT_EVIDENCE'
            AND subject_account_id IS NULL AND target_id IS NULL AND proposal_id IS NULL AND approval_id IS NULL
            AND delta_code = 'NONE')
        OR (actor_type = 'HUMAN' AND action_code <> 'AUDIT_EXPORT')
    )
);
CREATE INDEX protected_security_audit_time_idx ON vra.protected_security_audit (occurred_at, audit_id);
CREATE INDEX protected_security_audit_actor_idx ON vra.protected_security_audit (actor_account_id, occurred_at);
CREATE INDEX protected_security_audit_subject_idx ON vra.protected_security_audit (subject_account_id, occurred_at);
CREATE INDEX protected_security_audit_proposal_idx ON vra.protected_security_audit (proposal_id, approval_id);

-- Safe export projection only: reason and secret-bearing fields are absent.
CREATE TYPE vra.audit_evidence_row AS (
    audit_id UUID,
    actor_type VARCHAR(64),
    actor_account_id UUID,
    actor_workload VARCHAR(64),
    subject_account_id UUID,
    action_code VARCHAR(64),
    result_code VARCHAR(64),
    target_type VARCHAR(64),
    target_id UUID,
    target_reference VARCHAR(80),
    delta_code VARCHAR(64),
    subject_auth_generation_before BIGINT,
    subject_auth_generation_after BIGINT,
    occurred_at TIMESTAMPTZ,
    request_id UUID,
    proposal_id UUID,
    approval_id UUID
);
REVOKE ALL ON TYPE vra.audit_evidence_row FROM PUBLIC;
GRANT USAGE ON TYPE vra.audit_evidence_row TO vra_audit_evidence_reader;

CREATE TABLE vra.security_event (
    event_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    actor_type VARCHAR(64) NOT NULL,
    actor_account_id UUID,
    actor_workload VARCHAR(64),
    event_code VARCHAR(64) NOT NULL,
    outcome_code VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.statement_timestamp(),
    request_id UUID,
    target_type VARCHAR(64) NOT NULL,
    target_id UUID,
    target_reference VARCHAR(80),
    risk_code VARCHAR(64) NOT NULL,
    source_kind VARCHAR(32) NOT NULL DEFAULT 'APP',
    observer_workload VARCHAR(64),
    source_run_id UUID,
    source_event_digest BYTEA,
    source_time TIMESTAMPTZ,
    db_operation VARCHAR(16),
    CONSTRAINT security_event_pk PRIMARY KEY (event_id),
    CONSTRAINT security_event_source_uq UNIQUE (source_run_id, source_event_digest),
    CONSTRAINT security_event_actor_fk FOREIGN KEY (actor_account_id)
        REFERENCES vra.account (account_id),
    CONSTRAINT security_event_actor_ck CHECK (
        (actor_type = 'HUMAN' AND actor_account_id IS NOT NULL AND actor_workload IS NULL)
        OR (actor_type = 'WORKLOAD' AND actor_account_id IS NULL AND actor_workload IS NOT NULL
            AND actor_workload IN (
                'vra_runtime', 'vra_outbox_worker', 'vra_reconciliation_worker',
                'vra_async_operator', 'vra_projection_rebuilder', 'vra_async_observer',
                'vra_factor_fixture', 'vra_audit_evidence_reader', 'vra_security_telemetry_observer'
            ))
        OR (actor_type = 'UNKNOWN' AND actor_account_id IS NULL AND actor_workload IS NULL)
    ),
    CONSTRAINT security_event_event_ck CHECK (event_code IN (
        'AUTHENTICATION', 'AUTHORIZATION', 'FACTOR_CHALLENGE', 'FACTOR_ASSURANCE',
        'STAFF_ROLE_PROPOSAL', 'STAFF_ROLE_GRANT', 'STAFF_ROLE_REVOKE',
        'SESSION_ROTATION', 'SESSION_REVOCATION', 'WEBHOOK_VERIFICATION', 'WEBHOOK_REPLAY', 'AUDIT_TAMPER_ATTEMPT'
    )),
    CONSTRAINT security_event_outcome_ck CHECK (outcome_code IN ('SUCCEEDED', 'DENIED', 'FAILED', 'REPLAYED', 'DENIED_42501')),
    CONSTRAINT security_event_target_ck CHECK (target_type IN (
        'NONE', 'ACCOUNT', 'ORGANIZATION', 'ACCOUNT_RECORD', 'ORGANIZATION_RECORD',
        'INVENTORY', 'SESSION', 'FACTOR_CHALLENGE', 'ROLE_PROPOSAL', 'STAFF_ROLE', 'WEBHOOK_FIXTURE', 'PROTECTED_AUDIT'
    )),
    CONSTRAINT security_event_reference_ck CHECK (target_reference IS NULL OR pg_catalog.btrim(target_reference) <> ''),
    CONSTRAINT security_event_risk_ck CHECK (risk_code IN (
        'NONE', 'AUTHENTICATION_FAILURE', 'AUTHORIZATION_PROBE', 'FACTOR_FAILURE',
        'PRIVILEGED_ACTION', 'SESSION_CHANGE', 'WEBHOOK_FAILURE', 'WEBHOOK_REPLAY', 'AUDIT_INTEGRITY_PROBE'
    )),
    CONSTRAINT security_event_digest_ck CHECK (source_event_digest IS NULL OR pg_catalog.octet_length(source_event_digest) = 32),
    CONSTRAINT security_event_time_ck CHECK (
        pg_catalog.isfinite(occurred_at) AND (source_time IS NULL OR pg_catalog.isfinite(source_time))
    ),
    CONSTRAINT security_event_provenance_ck CHECK (
        (source_kind = 'APP' AND observer_workload IS NULL AND source_run_id IS NULL
            AND source_event_digest IS NULL AND source_time IS NULL AND db_operation IS NULL
            AND request_id IS NOT NULL AND outcome_code <> 'DENIED_42501')
        OR (source_kind = 'POSTGRES_ACL' AND observer_workload IS NOT NULL
            AND observer_workload = 'vra_security_telemetry_observer'
            AND source_run_id IS NOT NULL AND source_event_digest IS NOT NULL AND source_time IS NOT NULL
            AND db_operation IS NOT NULL AND db_operation IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE')
            AND actor_type = 'WORKLOAD' AND actor_account_id IS NULL AND actor_workload IS NOT NULL
            AND actor_workload IN ('vra_runtime', 'vra_outbox_worker', 'vra_reconciliation_worker')
            AND event_code = 'AUDIT_TAMPER_ATTEMPT' AND outcome_code = 'DENIED_42501'
            AND target_type = 'PROTECTED_AUDIT' AND target_id IS NULL AND target_reference IS NULL
            AND risk_code = 'AUDIT_INTEGRITY_PROBE' AND request_id IS NULL)
    )
);
CREATE INDEX security_event_time_idx ON vra.security_event (occurred_at, event_id);
CREATE INDEX security_event_actor_idx ON vra.security_event (actor_account_id, occurred_at);

CREATE TABLE vra.webhook_fixture_target (
    target_id UUID NOT NULL DEFAULT pg_catalog.gen_random_uuid(),
    state VARCHAR(64) NOT NULL DEFAULT 'NOT_READY',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT webhook_fixture_target_pk PRIMARY KEY (target_id),
    CONSTRAINT webhook_fixture_target_state_ck CHECK (state IN ('NOT_READY', 'READY', 'APPLIED')),
    CONSTRAINT webhook_fixture_target_version_ck CHECK (version >= 0)
);

CREATE TABLE vra.webhook_fixture_receipt (
    event_id VARCHAR(255) NOT NULL,
    body_digest BYTEA NOT NULL,
    target_id UUID NOT NULL,
    event_kind VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT pg_catalog.statement_timestamp(),
    CONSTRAINT webhook_fixture_receipt_pk PRIMARY KEY (event_id),
    CONSTRAINT webhook_fixture_receipt_event_id_ck CHECK (
        event_id <> '' AND pg_catalog.strpos(event_id, pg_catalog.chr(10)) = 0
        AND pg_catalog.strpos(event_id, pg_catalog.chr(13)) = 0
    ),
    CONSTRAINT webhook_fixture_receipt_digest_ck CHECK (pg_catalog.octet_length(body_digest) = 32),
    CONSTRAINT webhook_fixture_receipt_time_ck CHECK (pg_catalog.isfinite(first_seen_at)),
    CONSTRAINT webhook_fixture_receipt_target_fk FOREIGN KEY (target_id)
        REFERENCES vra.webhook_fixture_target (target_id),
    CONSTRAINT webhook_fixture_receipt_shape_ck CHECK (
        (event_kind = 'READY' AND status = 'APPLIED')
        OR (event_kind = 'APPLY' AND status IN ('PENDING', 'APPLIED'))
    )
);
CREATE INDEX webhook_fixture_receipt_target_idx ON vra.webhook_fixture_receipt (target_id, status, event_kind);

-- Scope this revocation to new authoritative objects; V1/V2/V3 ACLs are preserved.
REVOKE ALL ON TABLE
    vra.account, vra.external_identity_binding, vra.organization, vra.organization_membership,
    vra.inventory_operation_grant, vra.poc_account_record, vra.poc_organization_record,
    vra.browser_session, vra.factor_challenge, vra.factor_assertion_use,
    vra.staff_role_assignment, vra.security_role_proposal, vra.security_role_approval,
    vra.protected_security_audit, vra.security_event, vra.webhook_fixture_target, vra.webhook_fixture_receipt
    FROM PUBLIC;

REVOKE CREATE ON SCHEMA vra FROM PUBLIC;
REVOKE CREATE ON SCHEMA vra FROM
    vra_sync_executor, vra_security_executor, vra_telemetry_executor,
    vra_factor_fixture, vra_audit_evidence_reader, vra_security_telemetry_observer;
GRANT USAGE ON SCHEMA vra TO
    vra_sync_executor, vra_security_executor, vra_telemetry_executor,
    vra_factor_fixture, vra_audit_evidence_reader, vra_security_telemetry_observer;

GRANT SELECT ON TABLE
    vra.account, vra.external_identity_binding, vra.organization, vra.organization_membership,
    vra.inventory_operation_grant, vra.poc_account_record, vra.poc_organization_record,
    vra.browser_session, vra.factor_challenge, vra.factor_assertion_use,
    vra.staff_role_assignment, vra.security_role_proposal, vra.security_role_approval,
    vra.webhook_fixture_receipt, vra.webhook_fixture_target
    TO vra_runtime;
GRANT UPDATE (label, version) ON TABLE vra.poc_account_record TO vra_runtime;
GRANT UPDATE (label, version) ON TABLE vra.poc_organization_record TO vra_runtime;

-- APP inserts cannot select an origin or supply observer provenance.
GRANT INSERT (
    event_id, actor_type, actor_account_id, actor_workload, event_code, outcome_code,
    occurred_at, request_id, target_type, target_id, target_reference, risk_code
) ON TABLE vra.security_event TO vra_runtime;
GRANT SELECT (
    event_id, actor_type, actor_account_id, actor_workload, event_code, outcome_code,
    occurred_at, request_id, target_type, target_id, target_reference, risk_code, source_kind
) ON TABLE vra.security_event TO vra_runtime;

GRANT INSERT ON TABLE vra.webhook_fixture_receipt TO vra_runtime;
GRANT UPDATE (status) ON TABLE vra.webhook_fixture_receipt TO vra_runtime;
GRANT UPDATE (state, version) ON TABLE vra.webhook_fixture_target TO vra_runtime;
