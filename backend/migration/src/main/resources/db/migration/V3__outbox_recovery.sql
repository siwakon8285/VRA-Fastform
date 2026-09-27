-- Flyway executes this migration atomically through migrator -> owner.
-- The admin bootstrap must already have established Decision 17.
DO $$
BEGIN
    IF current_user <> 'vra_owner' OR NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_auth_members m
        WHERE m.roleid='vra_async_executor'::regrole AND m.member='vra_owner'::regrole
          AND m.set_option AND NOT m.inherit_option AND NOT m.admin_option
    ) THEN
        RAISE EXCEPTION 'expected owner and exact Decision 17 membership';
    END IF;
END $$;

CREATE TABLE vra.outbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL CHECK (btrim(event_type) <> ''),
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    reservation_id UUID NOT NULL UNIQUE REFERENCES vra.inventory_reservation(reservation_id),
    sku_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    location_id UUID NOT NULL,
    stock_status VARCHAR(32) NOT NULL CHECK (stock_status IN ('AVAILABLE','QUARANTINED')),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    CHECK (event_id <> reservation_id)
);

CREATE TABLE vra.outbox_delivery (
    event_id UUID PRIMARY KEY REFERENCES vra.outbox_event(event_id),
    target_code VARCHAR(40) NOT NULL CHECK (target_code IN ('RESERVATION_PROJECTION','VALIDATION_EXTERNAL_EFFECT')),
    state VARCHAR(32) NOT NULL CHECK (state IN ('READY','PROCESSING','RETRY_WAIT','RECONCILIATION_REQUIRED','SUCCEEDED','FAILED','CLOSED')),
    failure_class VARCHAR(32) CHECK (failure_class IN ('RETRYABLE_TRANSIENT','NON_RETRYABLE','UNKNOWN_OUTCOME','POISON','OPERATOR_REQUIRED')),
    reason_code VARCHAR(64) CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    next_eligible_at TIMESTAMPTZ,
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    claim_relinquished BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_attempt_count BIGINT NOT NULL DEFAULT 0,
    automatic_cycle INTEGER NOT NULL DEFAULT 1,
    cycle_claim_count INTEGER NOT NULL DEFAULT 0,
    cycle_claim_limit INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    state_changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT outbox_delivery_counts_ck CHECK (
        automatic_cycle >= 1 AND cycle_claim_limit BETWEEN 1 AND 16
        AND cycle_claim_count BETWEEN 0 AND cycle_claim_limit + 1
        AND delivery_attempt_count >= cycle_claim_count
        AND (cycle_claim_count <= cycle_claim_limit OR state IN ('FAILED','RECONCILIATION_REQUIRED','SUCCEEDED','CLOSED'))),
    CONSTRAINT outbox_delivery_shape_ck CHECK (
        (state='PROCESSING' AND claim_token IS NOT NULL AND claim_until IS NOT NULL
            AND next_eligible_at IS NULL AND failure_class IS NULL AND reason_code IS NULL
            AND cycle_claim_count > 0)
        OR (claim_token IS NULL AND claim_until IS NULL AND NOT claim_relinquished AND (
            (state IN ('READY','SUCCEEDED') AND next_eligible_at IS NULL AND failure_class IS NULL AND reason_code IS NULL)
            OR (state='RETRY_WAIT' AND next_eligible_at IS NOT NULL AND failure_class IS NOT NULL
                AND failure_class='RETRYABLE_TRANSIENT' AND reason_code IS NOT NULL)
            OR (state='RECONCILIATION_REQUIRED' AND next_eligible_at IS NULL AND failure_class IS NOT NULL
                AND failure_class='UNKNOWN_OUTCOME' AND reason_code IS NOT NULL)
            OR (state='FAILED' AND next_eligible_at IS NULL AND failure_class IS NOT NULL AND reason_code IS NOT NULL)
            OR (state='CLOSED' AND next_eligible_at IS NULL AND reason_code IS NOT NULL))))
);
CREATE INDEX outbox_delivery_due_idx ON vra.outbox_delivery(target_code,state,next_eligible_at,created_at,event_id)
    WHERE state IN ('READY','RETRY_WAIT');
CREATE INDEX outbox_delivery_expired_idx ON vra.outbox_delivery(target_code,claim_until,event_id) WHERE state='PROCESSING';
CREATE UNIQUE INDEX outbox_delivery_token_uq ON vra.outbox_delivery(claim_token) WHERE claim_token IS NOT NULL;
CREATE INDEX outbox_delivery_backlog_idx ON vra.outbox_delivery(state,created_at);

CREATE TABLE vra.outbox_delivery_history (
    history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES vra.outbox_event(event_id),
    action_code VARCHAR(40) NOT NULL CHECK (action_code IN ('CREATED','CLAIM','RECLAIM','RELINQUISH','SUCCEEDED','RETRY_SCHEDULED','FAILED','UNKNOWN_HANDOFF','RECOVERY_ONLY_FAILED','RECOVERY_ONLY_UNKNOWN_HANDOFF','RECONCILED_SUCCESS','RECONCILED_NO_EFFECT','RECONCILIATION_EXHAUSTED','CONTROL_REPLAY','CONTROL_RESUME','CONTROL_CLOSE')),
    from_state VARCHAR(32) CHECK (from_state IN ('READY','PROCESSING','RETRY_WAIT','RECONCILIATION_REQUIRED','SUCCEEDED','FAILED','CLOSED')),
    to_state VARCHAR(32) NOT NULL CHECK (to_state IN ('READY','PROCESSING','RETRY_WAIT','RECONCILIATION_REQUIRED','SUCCEEDED','FAILED','CLOSED')),
    claim_token UUID,
    lifetime_attempt BIGINT NOT NULL,
    automatic_cycle INTEGER NOT NULL CHECK (automatic_cycle >= 1),
    cycle_claim_count INTEGER NOT NULL,
    cycle_claim_limit INTEGER NOT NULL CHECK (cycle_claim_limit BETWEEN 1 AND 16),
    failure_class VARCHAR(32) CHECK (failure_class IN ('RETRYABLE_TRANSIENT','NON_RETRYABLE','UNKNOWN_OUTCOME','POISON','OPERATOR_REQUIRED')),
    reason_code VARCHAR(64) CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    actor_kind VARCHAR(24) NOT NULL CHECK (actor_kind IN ('RUNTIME','OUTBOX_WORKER','RECONCILIATION_WORKER','ASYNC_OPERATOR')),
    actor_ref VARCHAR(128),
    recorded_at TIMESTAMPTZ NOT NULL,
    CHECK (cycle_claim_count BETWEEN 0 AND cycle_claim_limit+1 AND lifetime_attempt >= cycle_claim_count)
);
CREATE INDEX outbox_delivery_history_event_idx ON vra.outbox_delivery_history(event_id,history_id);
CREATE UNIQUE INDEX outbox_delivery_history_claim_uq ON vra.outbox_delivery_history(claim_token)
    WHERE action_code IN ('CLAIM','RECLAIM');

CREATE TABLE vra.consumer_inbox (
    consumer_identity VARCHAR(64) NOT NULL CHECK (btrim(consumer_identity) <> ''),
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_identity,event_id)
);
CREATE TABLE vra.reservation_projection (
    reservation_id UUID PRIMARY KEY REFERENCES vra.inventory_reservation(reservation_id),
    sku_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    location_id UUID NOT NULL,
    stock_status VARCHAR(32) NOT NULL CHECK (stock_status IN ('AVAILABLE','QUARANTINED')),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    projected_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX reservation_projection_key_idx ON vra.reservation_projection(sku_id,owner_id,location_id,stock_status);

CREATE TABLE vra.reconciliation_case (
    case_id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES vra.outbox_delivery(event_id),
    state VARCHAR(32) NOT NULL CHECK (state IN ('PENDING','CHECKING','WAITING','RESOLVED','OPERATOR_REQUIRED','CLOSED')),
    external_knowledge VARCHAR(32) NOT NULL CHECK (external_knowledge IN ('UNKNOWN','CONFIRMED_SUCCEEDED','CONFIRMED_NO_EFFECT')),
    reason_code VARCHAR(64) NOT NULL CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    next_eligible_at TIMESTAMPTZ,
    claim_token UUID,
    claim_until TIMESTAMPTZ,
    lifetime_attempt_count BIGINT NOT NULL DEFAULT 0,
    automatic_cycle INTEGER NOT NULL DEFAULT 1,
    cycle_claim_count INTEGER NOT NULL DEFAULT 0,
    cycle_claim_limit INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    state_changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT reconciliation_case_counts_ck CHECK (
        automatic_cycle >= 1 AND cycle_claim_limit BETWEEN 1 AND 16
        AND cycle_claim_count BETWEEN 0 AND cycle_claim_limit+1
        AND lifetime_attempt_count >= cycle_claim_count
        AND (cycle_claim_count <= cycle_claim_limit OR state IN ('OPERATOR_REQUIRED','CLOSED'))),
    CONSTRAINT reconciliation_case_shape_ck CHECK (
        (state='CHECKING' AND external_knowledge='UNKNOWN' AND claim_token IS NOT NULL
            AND claim_until IS NOT NULL AND next_eligible_at IS NULL AND cycle_claim_count > 0)
        OR (claim_token IS NULL AND claim_until IS NULL AND (
            (state IN ('PENDING','WAITING') AND external_knowledge='UNKNOWN' AND next_eligible_at IS NOT NULL)
            OR (state='RESOLVED' AND external_knowledge IN ('CONFIRMED_SUCCEEDED','CONFIRMED_NO_EFFECT') AND next_eligible_at IS NULL)
            OR (state IN ('OPERATOR_REQUIRED','CLOSED') AND external_knowledge='UNKNOWN' AND next_eligible_at IS NULL))))
);
CREATE UNIQUE INDEX reconciliation_case_active_uq ON vra.reconciliation_case(event_id)
    WHERE state IN ('PENDING','CHECKING','WAITING','OPERATOR_REQUIRED');
CREATE INDEX reconciliation_case_due_idx ON vra.reconciliation_case(state,next_eligible_at,case_id) WHERE state IN ('PENDING','WAITING');
CREATE INDEX reconciliation_case_expired_idx ON vra.reconciliation_case(claim_until,case_id) WHERE state='CHECKING';
CREATE INDEX reconciliation_case_backlog_idx ON vra.reconciliation_case(state,created_at);

CREATE TABLE vra.reconciliation_history (
    history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES vra.reconciliation_case(case_id),
    action_code VARCHAR(40) NOT NULL CHECK (action_code IN ('CREATED','CHECK_CLAIM','CHECK_RECLAIM','INDETERMINATE_WAIT','CONFIRMED_SUCCESS','CONFIRMED_NO_EFFECT','EXHAUSTED','RECOVERY_ONLY_EXHAUSTED','CONTROL_RESUME','CONTROL_CLOSE')),
    from_state VARCHAR(32) CHECK (from_state IN ('PENDING','CHECKING','WAITING','RESOLVED','OPERATOR_REQUIRED','CLOSED')),
    to_state VARCHAR(32) NOT NULL CHECK (to_state IN ('PENDING','CHECKING','WAITING','RESOLVED','OPERATOR_REQUIRED','CLOSED')),
    claim_token UUID,
    lifetime_attempt BIGINT NOT NULL,
    automatic_cycle INTEGER NOT NULL CHECK (automatic_cycle >= 1),
    cycle_claim_count INTEGER NOT NULL,
    cycle_claim_limit INTEGER NOT NULL CHECK (cycle_claim_limit BETWEEN 1 AND 16),
    observation VARCHAR(32) CHECK (observation IN ('CONFIRMED_SUCCEEDED','CONFIRMED_NO_EFFECT','INDETERMINATE')),
    reason_code VARCHAR(64) NOT NULL CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    actor_kind VARCHAR(24) NOT NULL CHECK (actor_kind IN ('OUTBOX_WORKER','RECONCILIATION_WORKER','ASYNC_OPERATOR')),
    actor_ref VARCHAR(128),
    recorded_at TIMESTAMPTZ NOT NULL,
    CHECK (cycle_claim_count BETWEEN 0 AND cycle_claim_limit+1 AND lifetime_attempt >= cycle_claim_count)
);
CREATE INDEX reconciliation_history_case_idx ON vra.reconciliation_history(case_id,history_id);
CREATE UNIQUE INDEX reconciliation_history_claim_uq ON vra.reconciliation_history(claim_token)
    WHERE action_code IN ('CHECK_CLAIM','CHECK_RECLAIM');

CREATE FUNCTION vra.async_reject_immutable() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'immutable async evidence' USING ERRCODE='23514';
END $$;
REVOKE ALL ON FUNCTION vra.async_reject_immutable() FROM PUBLIC;

CREATE FUNCTION vra.async_guard_delivery_identity() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF (NEW.event_id,NEW.target_code,NEW.created_at) IS DISTINCT FROM
       (OLD.event_id,OLD.target_code,OLD.created_at) THEN
        RAISE EXCEPTION 'immutable delivery identity' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION vra.async_guard_delivery_identity() FROM PUBLIC;

CREATE FUNCTION vra.async_guard_case_identity() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF (NEW.case_id,NEW.event_id,NEW.created_at) IS DISTINCT FROM
       (OLD.case_id,OLD.event_id,OLD.created_at) THEN
        RAISE EXCEPTION 'immutable case identity' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION vra.async_guard_case_identity() FROM PUBLIC;

CREATE FUNCTION vra.async_check_pair() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    v_event UUID;
    v_state VARCHAR;
    v_target VARCHAR;
    v_active BIGINT;
    v_query BIGINT;
    v_operator BIGINT;
BEGIN
    v_event := CASE WHEN TG_OP='DELETE' THEN OLD.event_id ELSE NEW.event_id END;
    SELECT d.state,d.target_code INTO v_state,v_target FROM vra.outbox_delivery d WHERE d.event_id=v_event;
    SELECT count(*),count(*) FILTER (WHERE c.state IN ('PENDING','CHECKING','WAITING')),
        count(*) FILTER (WHERE c.state='OPERATOR_REQUIRED')
    INTO v_active,v_query,v_operator FROM vra.reconciliation_case c
    WHERE c.event_id=v_event AND c.state IN ('PENDING','CHECKING','WAITING','OPERATOR_REQUIRED');
    IF (v_active > 0 AND v_target IS DISTINCT FROM 'VALIDATION_EXTERNAL_EFFECT')
        OR (v_state='RECONCILIATION_REQUIRED' AND (v_active<>1 OR v_query<>1))
        OR (v_state='FAILED' AND v_active>0 AND (v_active<>1 OR v_operator<>1))
        OR (v_state IS DISTINCT FROM 'RECONCILIATION_REQUIRED' AND v_state IS DISTINCT FROM 'FAILED' AND v_active<>0)
    THEN
        RAISE EXCEPTION 'inconsistent delivery/reconciliation pair' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION vra.async_check_pair() FROM PUBLIC;

CREATE FUNCTION vra.async_check_delivery_recovery() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM vra.outbox_delivery d WHERE d.event_id=NEW.event_id
        AND d.cycle_claim_count=d.cycle_claim_limit+1
        AND NOT EXISTS (
            SELECT 1 FROM vra.outbox_delivery_history h WHERE h.event_id=d.event_id
            AND h.automatic_cycle=d.automatic_cycle AND h.cycle_claim_limit=d.cycle_claim_limit
            AND h.cycle_claim_count=d.cycle_claim_count AND h.lifetime_attempt=d.delivery_attempt_count
            AND h.claim_token IS NOT NULL
            AND h.action_code IN ('RECOVERY_ONLY_FAILED','RECOVERY_ONLY_UNKNOWN_HANDOFF'))
    ) THEN
        RAISE EXCEPTION 'missing delivery recovery-only evidence' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION vra.async_check_delivery_recovery() FROM PUBLIC;

CREATE FUNCTION vra.async_check_case_recovery() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM vra.reconciliation_case c WHERE c.case_id=NEW.case_id
        AND c.cycle_claim_count=c.cycle_claim_limit+1
        AND NOT EXISTS (
            SELECT 1 FROM vra.reconciliation_history h WHERE h.case_id=c.case_id
            AND h.automatic_cycle=c.automatic_cycle AND h.cycle_claim_limit=c.cycle_claim_limit
            AND h.cycle_claim_count=c.cycle_claim_count AND h.lifetime_attempt=c.lifetime_attempt_count
            AND h.claim_token IS NOT NULL AND h.action_code='RECOVERY_ONLY_EXHAUSTED')
    ) THEN
        RAISE EXCEPTION 'missing reconciliation recovery-only evidence' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END $$;
REVOKE ALL ON FUNCTION vra.async_check_case_recovery() FROM PUBLIC;

CREATE FUNCTION vra.async_publish_reservation(p_event_id UUID,p_reservation_id UUID,p_target_code VARCHAR)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF p_event_id IS NULL OR p_reservation_id IS NULL OR p_event_id=p_reservation_id
        OR p_target_code IS NULL OR p_target_code NOT IN ('RESERVATION_PROJECTION','VALIDATION_EXTERNAL_EFFECT') THEN
        RAISE EXCEPTION 'invalid publish arguments' USING ERRCODE='22023';
    END IF;
    INSERT INTO vra.outbox_event(event_id,event_type,schema_version,occurred_at,reservation_id,
        sku_id,owner_id,location_id,stock_status,quantity)
    SELECT p_event_id,'inventory.reservation.created',1,r.created_at,r.reservation_id,
        r.sku_id,r.owner_id,r.location_id,r.stock_status,r.quantity
    FROM vra.inventory_reservation r WHERE r.reservation_id=p_reservation_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'reservation absent' USING ERRCODE='22023'; END IF;
    INSERT INTO vra.outbox_delivery(event_id,target_code,state,automatic_cycle,cycle_claim_count,
        cycle_claim_limit,created_at,state_changed_at)
    VALUES(p_event_id,p_target_code,'READY',1,0,5,pg_catalog.statement_timestamp(),pg_catalog.statement_timestamp());
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,actor_kind,recorded_at)
    VALUES(p_event_id,'CREATED',NULL,'READY',0,1,0,5,'RUNTIME',pg_catalog.statement_timestamp());
    RETURN p_event_id;
END $$;
REVOKE ALL ON FUNCTION vra.async_publish_reservation(UUID,UUID,VARCHAR) FROM PUBLIC;

CREATE FUNCTION vra.async_claim_delivery(p_target_code VARCHAR,p_worker_ref VARCHAR,p_batch_limit INTEGER,p_lease_ms BIGINT)
RETURNS TABLE(event_id UUID,claim_token UUID,target_code VARCHAR,reclaimed BOOLEAN,
    delivery_attempt_count BIGINT,automatic_cycle INTEGER,cycle_claim_count INTEGER,cycle_claim_limit INTEGER)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    d vra.outbox_delivery%ROWTYPE;
    v_token UUID;
    v_case UUID;
    v_action VARCHAR;
    v_recovery BOOLEAN;
BEGIN
    IF p_target_code IS NULL OR p_target_code NOT IN ('RESERVATION_PROJECTION','VALIDATION_EXTERNAL_EFFECT')
        OR p_worker_ref IS NULL OR length(btrim(p_worker_ref))=0 OR length(p_worker_ref)>128
        OR p_batch_limit IS NULL OR p_batch_limit NOT BETWEEN 1 AND 128
        OR p_lease_ms IS NULL OR p_lease_ms NOT BETWEEN 1 AND 86400000 THEN
        RAISE EXCEPTION 'invalid claim arguments' USING ERRCODE='22023';
    END IF;
    FOR d IN SELECT x.* FROM vra.outbox_delivery x WHERE x.target_code=p_target_code AND (
        (x.state='READY' AND x.cycle_claim_count<x.cycle_claim_limit)
        OR (x.state='RETRY_WAIT' AND x.next_eligible_at<=pg_catalog.statement_timestamp() AND x.cycle_claim_count<x.cycle_claim_limit)
        OR (x.state='PROCESSING' AND x.cycle_claim_count<=x.cycle_claim_limit
            AND (x.claim_until<=pg_catalog.statement_timestamp() OR x.claim_relinquished)))
        ORDER BY COALESCE(x.next_eligible_at,x.claim_until,x.created_at),x.created_at,x.event_id
        LIMIT p_batch_limit FOR UPDATE SKIP LOCKED
    LOOP
        v_token := pg_catalog.gen_random_uuid();
        v_recovery := d.state='PROCESSING' AND d.cycle_claim_count=d.cycle_claim_limit;
        IF v_recovery THEN
            v_action := CASE WHEN d.target_code='RESERVATION_PROJECTION' THEN 'RECOVERY_ONLY_FAILED' ELSE 'RECOVERY_ONLY_UNKNOWN_HANDOFF' END;
            UPDATE vra.outbox_delivery x SET
                state=CASE WHEN d.target_code='RESERVATION_PROJECTION' THEN 'FAILED' ELSE 'RECONCILIATION_REQUIRED' END,
                failure_class=CASE WHEN d.target_code='RESERVATION_PROJECTION' THEN 'OPERATOR_REQUIRED' ELSE 'UNKNOWN_OUTCOME' END,
                reason_code=CASE WHEN d.target_code='RESERVATION_PROJECTION' THEN 'RETRY_EXHAUSTED' ELSE 'RECLAIM_POSSIBLE_EXTERNAL_SEND' END,
                claim_token=NULL,claim_until=NULL,claim_relinquished=FALSE,next_eligible_at=NULL,
                delivery_attempt_count=x.delivery_attempt_count+1,cycle_claim_count=x.cycle_claim_count+1,
                state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=d.event_id;
        ELSE
            UPDATE vra.outbox_delivery x SET state='PROCESSING',claim_token=v_token,
                claim_until=pg_catalog.statement_timestamp()+p_lease_ms*INTERVAL '1 millisecond',
                claim_relinquished=FALSE,next_eligible_at=NULL,failure_class=NULL,reason_code=NULL,
                delivery_attempt_count=x.delivery_attempt_count+1,cycle_claim_count=x.cycle_claim_count+1,
                state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=d.event_id;
        END IF;
        INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,claim_token,
            lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,actor_ref,recorded_at)
        SELECT x.event_id,CASE WHEN d.state='PROCESSING' THEN 'RECLAIM' ELSE 'CLAIM' END,d.state,x.state,v_token,
            x.delivery_attempt_count,x.automatic_cycle,x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,
            'OUTBOX_WORKER',p_worker_ref,pg_catalog.statement_timestamp() FROM vra.outbox_delivery x WHERE x.event_id=d.event_id;
        IF v_recovery THEN
            INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,claim_token,
                lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,actor_ref,recorded_at)
            SELECT x.event_id,v_action,d.state,x.state,v_token,x.delivery_attempt_count,x.automatic_cycle,
                x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'OUTBOX_WORKER',p_worker_ref,
                pg_catalog.statement_timestamp() FROM vra.outbox_delivery x WHERE x.event_id=d.event_id;
            IF d.target_code='VALIDATION_EXTERNAL_EFFECT' THEN
                v_case := pg_catalog.gen_random_uuid();
                INSERT INTO vra.reconciliation_case(case_id,event_id,state,external_knowledge,reason_code,
                    next_eligible_at,automatic_cycle,cycle_claim_count,cycle_claim_limit,created_at,state_changed_at)
                VALUES(v_case,d.event_id,'PENDING','UNKNOWN','RECLAIM_POSSIBLE_EXTERNAL_SEND',
                    pg_catalog.statement_timestamp(),1,0,4,pg_catalog.statement_timestamp(),pg_catalog.statement_timestamp());
                INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,lifetime_attempt,
                    automatic_cycle,cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,actor_ref,recorded_at)
                VALUES(v_case,'CREATED',NULL,'PENDING',0,1,0,4,'RECLAIM_POSSIBLE_EXTERNAL_SEND',
                    'OUTBOX_WORKER',p_worker_ref,pg_catalog.statement_timestamp());
            END IF;
        ELSE
            RETURN QUERY SELECT x.event_id,x.claim_token,x.target_code,d.state='PROCESSING',
                x.delivery_attempt_count,x.automatic_cycle,x.cycle_claim_count,x.cycle_claim_limit
                FROM vra.outbox_delivery x WHERE x.event_id=d.event_id;
        END IF;
    END LOOP;
END $$;
REVOKE ALL ON FUNCTION vra.async_claim_delivery(VARCHAR,VARCHAR,INTEGER,BIGINT) FROM PUBLIC;

CREATE FUNCTION vra.async_relinquish_delivery(p_event_id UUID,p_token UUID) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE d vra.outbox_delivery%ROWTYPE; v_state VARCHAR;
BEGIN
    IF p_event_id IS NULL OR p_token IS NULL THEN
        RAISE EXCEPTION 'invalid relinquish_delivery arguments' USING ERRCODE='22023';
    END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR d.state<>'PROCESSING' OR d.claim_token IS DISTINCT FROM p_token OR d.claim_relinquished THEN
        RETURN FALSE;
    END IF;
    UPDATE vra.outbox_delivery x SET claim_relinquished=TRUE,claim_until=LEAST(x.claim_until,pg_catalog.statement_timestamp()),state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.event_id=p_event_id AND x.state='PROCESSING' AND x.claim_token=p_token AND NOT x.claim_relinquished
    RETURNING x.state INTO v_state;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
    SELECT x.event_id,'RELINQUISH',d.state,x.state,p_token,x.delivery_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'OUTBOX_WORKER',pg_catalog.statement_timestamp()
    FROM vra.outbox_delivery x WHERE x.event_id=p_event_id;
    RETURN TRUE;
END $$;
REVOKE ALL ON FUNCTION vra.async_relinquish_delivery(UUID,UUID) FROM PUBLIC;

CREATE FUNCTION vra.async_complete_delivery(p_event_id UUID,p_token UUID) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE d vra.outbox_delivery%ROWTYPE; v_state VARCHAR;
BEGIN
    IF p_event_id IS NULL OR p_token IS NULL THEN
        RAISE EXCEPTION 'invalid complete_delivery arguments' USING ERRCODE='22023';
    END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR d.state<>'PROCESSING' OR d.claim_token IS DISTINCT FROM p_token OR d.claim_relinquished THEN
        RETURN FALSE;
    END IF;
    UPDATE vra.outbox_delivery x SET state='SUCCEEDED',claim_token=NULL,claim_until=NULL,next_eligible_at=NULL,failure_class=NULL,reason_code=NULL,state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.event_id=p_event_id AND x.state='PROCESSING' AND x.claim_token=p_token AND NOT x.claim_relinquished
    RETURNING x.state INTO v_state;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
    SELECT x.event_id,'SUCCEEDED',d.state,x.state,p_token,x.delivery_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'OUTBOX_WORKER',pg_catalog.statement_timestamp()
    FROM vra.outbox_delivery x WHERE x.event_id=p_event_id;
    RETURN TRUE;
END $$;
REVOKE ALL ON FUNCTION vra.async_complete_delivery(UUID,UUID) FROM PUBLIC;

CREATE FUNCTION vra.async_retry_delivery(p_event_id UUID,p_token UUID,p_reason_code VARCHAR,p_delay_ms BIGINT) RETURNS VARCHAR
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE d vra.outbox_delivery%ROWTYPE; v_state VARCHAR;
BEGIN
    IF p_event_id IS NULL OR p_token IS NULL OR p_reason_code IS NULL OR p_reason_code<>'RETRYABLE_TRANSIENT' OR p_delay_ms IS NULL OR p_delay_ms NOT BETWEEN 1 AND 86400000 THEN
        RAISE EXCEPTION 'invalid retry_delivery arguments' USING ERRCODE='22023';
    END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR d.state<>'PROCESSING' OR d.claim_token IS DISTINCT FROM p_token OR d.claim_relinquished THEN
        RETURN NULL;
    END IF;
    UPDATE vra.outbox_delivery x SET state=CASE WHEN d.cycle_claim_count<d.cycle_claim_limit THEN 'RETRY_WAIT' ELSE 'FAILED' END,claim_token=NULL,claim_until=NULL,next_eligible_at=CASE WHEN d.cycle_claim_count<d.cycle_claim_limit THEN pg_catalog.statement_timestamp()+p_delay_ms*INTERVAL '1 millisecond' ELSE NULL END,failure_class='RETRYABLE_TRANSIENT',reason_code=CASE WHEN d.cycle_claim_count<d.cycle_claim_limit THEN p_reason_code ELSE 'RETRY_EXHAUSTED' END,state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.event_id=p_event_id AND x.state='PROCESSING' AND x.claim_token=p_token AND NOT x.claim_relinquished
    RETURNING x.state INTO v_state;
    IF NOT FOUND THEN RETURN NULL; END IF;
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
    SELECT x.event_id,CASE WHEN d.cycle_claim_count<d.cycle_claim_limit THEN 'RETRY_SCHEDULED' ELSE 'FAILED' END,d.state,x.state,p_token,x.delivery_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'OUTBOX_WORKER',pg_catalog.statement_timestamp()
    FROM vra.outbox_delivery x WHERE x.event_id=p_event_id;
    RETURN v_state;
END $$;
REVOKE ALL ON FUNCTION vra.async_retry_delivery(UUID,UUID,VARCHAR,BIGINT) FROM PUBLIC;

CREATE FUNCTION vra.async_fail_delivery(p_event_id UUID,p_token UUID,p_failure_class VARCHAR,p_reason_code VARCHAR) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE d vra.outbox_delivery%ROWTYPE; v_state VARCHAR;
BEGIN
    IF p_event_id IS NULL OR p_token IS NULL OR p_failure_class IS NULL OR p_reason_code IS NULL OR NOT ((p_failure_class='NON_RETRYABLE' AND p_reason_code='NON_RETRYABLE') OR (p_failure_class='POISON' AND p_reason_code IN ('POISON','UNSUPPORTED_EVENT_CONTRACT')) OR (p_failure_class='OPERATOR_REQUIRED' AND p_reason_code='OPERATOR_REQUIRED')) THEN
        RAISE EXCEPTION 'invalid fail_delivery arguments' USING ERRCODE='22023';
    END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR d.state<>'PROCESSING' OR d.claim_token IS DISTINCT FROM p_token OR d.claim_relinquished THEN
        RETURN FALSE;
    END IF;
    UPDATE vra.outbox_delivery x SET state='FAILED',claim_token=NULL,claim_until=NULL,next_eligible_at=NULL,failure_class=p_failure_class,reason_code=p_reason_code,state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.event_id=p_event_id AND x.state='PROCESSING' AND x.claim_token=p_token AND NOT x.claim_relinquished
    RETURNING x.state INTO v_state;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
    SELECT x.event_id,'FAILED',d.state,x.state,p_token,x.delivery_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'OUTBOX_WORKER',pg_catalog.statement_timestamp()
    FROM vra.outbox_delivery x WHERE x.event_id=p_event_id;
    RETURN TRUE;
END $$;
REVOKE ALL ON FUNCTION vra.async_fail_delivery(UUID,UUID,VARCHAR,VARCHAR) FROM PUBLIC;

CREATE FUNCTION vra.async_handoff_unknown(p_event_id UUID,p_token UUID,p_reason_code VARCHAR)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE d vra.outbox_delivery%ROWTYPE; v_case UUID;
BEGIN
    IF p_event_id IS NULL OR p_token IS NULL OR p_reason_code IS NULL
        OR p_reason_code NOT IN ('UNKNOWN_EXTERNAL_RESULT','RECLAIM_POSSIBLE_EXTERNAL_SEND') THEN
        RAISE EXCEPTION 'invalid unknown handoff arguments' USING ERRCODE='22023';
    END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR d.state<>'PROCESSING' OR d.claim_token IS DISTINCT FROM p_token
        OR d.claim_relinquished OR d.target_code<>'VALIDATION_EXTERNAL_EFFECT' THEN RETURN NULL; END IF;
    UPDATE vra.outbox_delivery x SET state='RECONCILIATION_REQUIRED',failure_class='UNKNOWN_OUTCOME',
        reason_code=p_reason_code,claim_token=NULL,claim_until=NULL,next_eligible_at=NULL,
        state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.event_id=p_event_id AND x.state='PROCESSING' AND x.claim_token=p_token AND NOT x.claim_relinquished;
    IF NOT FOUND THEN RETURN NULL; END IF;
    v_case := pg_catalog.gen_random_uuid();
    INSERT INTO vra.reconciliation_case(case_id,event_id,state,external_knowledge,reason_code,next_eligible_at,
        automatic_cycle,cycle_claim_count,cycle_claim_limit,created_at,state_changed_at)
    VALUES(v_case,p_event_id,'PENDING','UNKNOWN',p_reason_code,pg_catalog.statement_timestamp(),1,0,4,
        pg_catalog.statement_timestamp(),pg_catalog.statement_timestamp());
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
    SELECT x.event_id,'UNKNOWN_HANDOFF',d.state,x.state,p_token,x.delivery_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'OUTBOX_WORKER',pg_catalog.statement_timestamp()
    FROM vra.outbox_delivery x WHERE x.event_id=p_event_id;
    INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,lifetime_attempt,
        automatic_cycle,cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,recorded_at)
    VALUES(v_case,'CREATED',NULL,'PENDING',0,1,0,4,p_reason_code,'OUTBOX_WORKER',pg_catalog.statement_timestamp());
    RETURN v_case;
END $$;
REVOKE ALL ON FUNCTION vra.async_handoff_unknown(UUID,UUID,VARCHAR) FROM PUBLIC;

CREATE FUNCTION vra.async_claim_reconciliation(p_worker_ref VARCHAR,p_batch_limit INTEGER,p_lease_ms BIGINT)
RETURNS TABLE(case_id UUID,event_id UUID,claim_token UUID,lifetime_attempt_count BIGINT,
    automatic_cycle INTEGER,cycle_claim_count INTEGER,cycle_claim_limit INTEGER)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE candidate RECORD; c vra.reconciliation_case%ROWTYPE; v_token UUID; v_recovery BOOLEAN;
BEGIN
    IF p_worker_ref IS NULL OR length(btrim(p_worker_ref))=0 OR length(p_worker_ref)>128
        OR p_batch_limit IS NULL OR p_batch_limit NOT BETWEEN 1 AND 128
        OR p_lease_ms IS NULL OR p_lease_ms NOT BETWEEN 1 AND 86400000 THEN
        RAISE EXCEPTION 'invalid reconciliation claim arguments' USING ERRCODE='22023';
    END IF;
    FOR candidate IN
        SELECT x.case_id,x.event_id FROM vra.reconciliation_case x JOIN vra.outbox_delivery d ON d.event_id=x.event_id
        WHERE d.state='RECONCILIATION_REQUIRED' AND (
            (x.state IN ('PENDING','WAITING') AND x.next_eligible_at<=pg_catalog.statement_timestamp() AND x.cycle_claim_count<x.cycle_claim_limit)
            OR (x.state='CHECKING' AND x.claim_until<=pg_catalog.statement_timestamp() AND x.cycle_claim_count<=x.cycle_claim_limit))
        ORDER BY COALESCE(x.next_eligible_at,x.claim_until,x.created_at),x.created_at,x.case_id
        LIMIT p_batch_limit FOR UPDATE OF d SKIP LOCKED
    LOOP
        SELECT x.* INTO c FROM vra.reconciliation_case x WHERE x.case_id=candidate.case_id FOR UPDATE;
        IF NOT FOUND OR NOT (
            (c.state IN ('PENDING','WAITING') AND c.next_eligible_at<=pg_catalog.statement_timestamp() AND c.cycle_claim_count<c.cycle_claim_limit)
            OR (c.state='CHECKING' AND c.claim_until<=pg_catalog.statement_timestamp() AND c.cycle_claim_count<=c.cycle_claim_limit)) THEN CONTINUE; END IF;
        v_token := pg_catalog.gen_random_uuid();
        v_recovery := c.state='CHECKING' AND c.cycle_claim_count=c.cycle_claim_limit;
        IF v_recovery THEN
            UPDATE vra.outbox_delivery d SET state='FAILED',failure_class='UNKNOWN_OUTCOME',reason_code='RECONCILIATION_EXHAUSTED',
                state_changed_at=pg_catalog.statement_timestamp() WHERE d.event_id=c.event_id;
            UPDATE vra.reconciliation_case x SET state='OPERATOR_REQUIRED',reason_code='RECONCILIATION_EXHAUSTED',
                claim_token=NULL,claim_until=NULL,next_eligible_at=NULL,lifetime_attempt_count=x.lifetime_attempt_count+1,
                cycle_claim_count=x.cycle_claim_count+1,state_changed_at=pg_catalog.statement_timestamp() WHERE x.case_id=c.case_id;
        ELSE
            UPDATE vra.reconciliation_case x SET state='CHECKING',claim_token=v_token,
                claim_until=pg_catalog.statement_timestamp()+p_lease_ms*INTERVAL '1 millisecond',next_eligible_at=NULL,
                lifetime_attempt_count=x.lifetime_attempt_count+1,cycle_claim_count=x.cycle_claim_count+1,
                state_changed_at=pg_catalog.statement_timestamp() WHERE x.case_id=c.case_id;
        END IF;
        INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,claim_token,
            lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,actor_ref,recorded_at)
        SELECT x.case_id,CASE WHEN c.state='CHECKING' THEN 'CHECK_RECLAIM' ELSE 'CHECK_CLAIM' END,c.state,x.state,v_token,
            x.lifetime_attempt_count,x.automatic_cycle,x.cycle_claim_count,x.cycle_claim_limit,x.reason_code,
            'RECONCILIATION_WORKER',p_worker_ref,pg_catalog.statement_timestamp() FROM vra.reconciliation_case x WHERE x.case_id=c.case_id;
        IF v_recovery THEN
            INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,claim_token,
                lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,actor_ref,recorded_at)
            SELECT x.case_id,'RECOVERY_ONLY_EXHAUSTED',c.state,x.state,v_token,x.lifetime_attempt_count,x.automatic_cycle,
                x.cycle_claim_count,x.cycle_claim_limit,x.reason_code,'RECONCILIATION_WORKER',p_worker_ref,pg_catalog.statement_timestamp()
            FROM vra.reconciliation_case x WHERE x.case_id=c.case_id;
            INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,
                lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,actor_ref,recorded_at)
            SELECT d.event_id,'RECONCILIATION_EXHAUSTED','RECONCILIATION_REQUIRED',d.state,d.delivery_attempt_count,d.automatic_cycle,
                d.cycle_claim_count,d.cycle_claim_limit,d.failure_class,d.reason_code,'RECONCILIATION_WORKER',p_worker_ref,
                pg_catalog.statement_timestamp() FROM vra.outbox_delivery d WHERE d.event_id=c.event_id;
        ELSE
            RETURN QUERY SELECT x.case_id,x.event_id,x.claim_token,x.lifetime_attempt_count,
                x.automatic_cycle,x.cycle_claim_count,x.cycle_claim_limit FROM vra.reconciliation_case x WHERE x.case_id=c.case_id;
        END IF;
    END LOOP;
END $$;
REVOKE ALL ON FUNCTION vra.async_claim_reconciliation(VARCHAR,INTEGER,BIGINT) FROM PUBLIC;

CREATE FUNCTION vra.async_wait_reconciliation(p_case_id UUID,p_token UUID,p_reason_code VARCHAR,p_delay_ms BIGINT) RETURNS VARCHAR
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    d vra.outbox_delivery%ROWTYPE; c vra.reconciliation_case%ROWTYPE; v_event UUID;
    v_case_state VARCHAR; v_case_action VARCHAR; v_knowledge VARCHAR := 'UNKNOWN';
    v_delivery_state VARCHAR; v_delivery_action VARCHAR; v_reason VARCHAR; v_class VARCHAR;
    v_observation VARCHAR; v_due TIMESTAMPTZ; v_delivery_due TIMESTAMPTZ;
BEGIN
    IF p_case_id IS NULL OR p_token IS NULL OR p_reason_code IS NULL OR p_reason_code<>'UNKNOWN_EXTERNAL_RESULT' OR p_delay_ms IS NULL OR p_delay_ms NOT BETWEEN 1 AND 86400000 THEN RAISE EXCEPTION 'invalid wait_reconciliation arguments' USING ERRCODE='22023'; END IF;
    SELECT x.event_id INTO v_event FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR d.state<>'RECONCILIATION_REQUIRED' OR d.target_code<>'VALIDATION_EXTERNAL_EFFECT' THEN RETURN NULL; END IF;
    SELECT x.* INTO c FROM vra.reconciliation_case x WHERE x.case_id=p_case_id AND x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR c.state<>'CHECKING' OR c.claim_token IS DISTINCT FROM p_token OR c.external_knowledge<>'UNKNOWN' THEN RETURN NULL; END IF;
    IF c.cycle_claim_count<c.cycle_claim_limit THEN
        v_case_state := 'WAITING'; v_case_action := 'INDETERMINATE_WAIT'; v_reason := p_reason_code;
        v_due := pg_catalog.statement_timestamp()+p_delay_ms*INTERVAL '1 millisecond';
    ELSE
        v_case_state := 'OPERATOR_REQUIRED'; v_case_action := 'EXHAUSTED'; v_reason := 'RECONCILIATION_EXHAUSTED';
        v_delivery_state := 'FAILED'; v_delivery_action := 'RECONCILIATION_EXHAUSTED'; v_class := 'UNKNOWN_OUTCOME';
    END IF;
    v_observation := 'INDETERMINATE';
    IF v_delivery_state IS NOT NULL THEN
        UPDATE vra.outbox_delivery x SET state=v_delivery_state,failure_class=v_class,
            reason_code=CASE WHEN v_delivery_state='SUCCEEDED' THEN NULL ELSE v_reason END,
            next_eligible_at=v_delivery_due,claim_token=NULL,claim_until=NULL,claim_relinquished=FALSE,
            state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=v_event AND x.state='RECONCILIATION_REQUIRED';
        IF NOT FOUND THEN RETURN NULL; END IF;
    END IF;
    UPDATE vra.reconciliation_case x SET state=v_case_state,external_knowledge=v_knowledge,reason_code=v_reason,
        next_eligible_at=v_due,claim_token=NULL,claim_until=NULL,state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.case_id=p_case_id AND x.state='CHECKING' AND x.claim_token=p_token;
    IF NOT FOUND THEN RAISE EXCEPTION 'locked case changed unexpectedly'; END IF;
    INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,observation,reason_code,actor_kind,recorded_at)
    SELECT x.case_id,v_case_action,c.state,x.state,p_token,x.lifetime_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,v_observation,x.reason_code,'RECONCILIATION_WORKER',pg_catalog.statement_timestamp()
    FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF v_delivery_state IS NOT NULL THEN
        INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,
            lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
        SELECT x.event_id,v_delivery_action,d.state,x.state,x.delivery_attempt_count,x.automatic_cycle,
            x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'RECONCILIATION_WORKER',pg_catalog.statement_timestamp()
        FROM vra.outbox_delivery x WHERE x.event_id=v_event;
    END IF;
    RETURN v_case_state;
END $$;
REVOKE ALL ON FUNCTION vra.async_wait_reconciliation(UUID,UUID,VARCHAR,BIGINT) FROM PUBLIC;

CREATE FUNCTION vra.async_confirm_external_success(p_case_id UUID,p_token UUID) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    d vra.outbox_delivery%ROWTYPE; c vra.reconciliation_case%ROWTYPE; v_event UUID;
    v_case_state VARCHAR; v_case_action VARCHAR; v_knowledge VARCHAR := 'UNKNOWN';
    v_delivery_state VARCHAR; v_delivery_action VARCHAR; v_reason VARCHAR; v_class VARCHAR;
    v_observation VARCHAR; v_due TIMESTAMPTZ; v_delivery_due TIMESTAMPTZ;
BEGIN
    IF p_case_id IS NULL OR p_token IS NULL THEN RAISE EXCEPTION 'invalid confirm_external_success arguments' USING ERRCODE='22023'; END IF;
    SELECT x.event_id INTO v_event FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR d.state<>'RECONCILIATION_REQUIRED' OR d.target_code<>'VALIDATION_EXTERNAL_EFFECT' THEN RETURN FALSE; END IF;
    SELECT x.* INTO c FROM vra.reconciliation_case x WHERE x.case_id=p_case_id AND x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR c.state<>'CHECKING' OR c.claim_token IS DISTINCT FROM p_token OR c.external_knowledge<>'UNKNOWN' THEN RETURN FALSE; END IF;
    v_case_state := 'RESOLVED'; v_case_action := 'CONFIRMED_SUCCESS'; v_knowledge := 'CONFIRMED_SUCCEEDED';
    v_observation := 'CONFIRMED_SUCCEEDED'; v_reason := 'CONFIRMED_SUCCEEDED';
    v_delivery_state := 'SUCCEEDED'; v_delivery_action := 'RECONCILED_SUCCESS'; v_class := NULL;
    IF v_delivery_state IS NOT NULL THEN
        UPDATE vra.outbox_delivery x SET state=v_delivery_state,failure_class=v_class,
            reason_code=CASE WHEN v_delivery_state='SUCCEEDED' THEN NULL ELSE v_reason END,
            next_eligible_at=v_delivery_due,claim_token=NULL,claim_until=NULL,claim_relinquished=FALSE,
            state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=v_event AND x.state='RECONCILIATION_REQUIRED';
        IF NOT FOUND THEN RETURN FALSE; END IF;
    END IF;
    UPDATE vra.reconciliation_case x SET state=v_case_state,external_knowledge=v_knowledge,reason_code=v_reason,
        next_eligible_at=v_due,claim_token=NULL,claim_until=NULL,state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.case_id=p_case_id AND x.state='CHECKING' AND x.claim_token=p_token;
    IF NOT FOUND THEN RAISE EXCEPTION 'locked case changed unexpectedly'; END IF;
    INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,observation,reason_code,actor_kind,recorded_at)
    SELECT x.case_id,v_case_action,c.state,x.state,p_token,x.lifetime_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,v_observation,x.reason_code,'RECONCILIATION_WORKER',pg_catalog.statement_timestamp()
    FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF v_delivery_state IS NOT NULL THEN
        INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,
            lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
        SELECT x.event_id,v_delivery_action,d.state,x.state,x.delivery_attempt_count,x.automatic_cycle,
            x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'RECONCILIATION_WORKER',pg_catalog.statement_timestamp()
        FROM vra.outbox_delivery x WHERE x.event_id=v_event;
    END IF;
    RETURN TRUE;
END $$;
REVOKE ALL ON FUNCTION vra.async_confirm_external_success(UUID,UUID) FROM PUBLIC;

CREATE FUNCTION vra.async_confirm_external_no_effect(p_case_id UUID,p_token UUID,p_retry_safe BOOLEAN,p_retry_delay_ms BIGINT) RETURNS VARCHAR
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    d vra.outbox_delivery%ROWTYPE; c vra.reconciliation_case%ROWTYPE; v_event UUID;
    v_case_state VARCHAR; v_case_action VARCHAR; v_knowledge VARCHAR := 'UNKNOWN';
    v_delivery_state VARCHAR; v_delivery_action VARCHAR; v_reason VARCHAR; v_class VARCHAR;
    v_observation VARCHAR; v_due TIMESTAMPTZ; v_delivery_due TIMESTAMPTZ;
BEGIN
    IF p_case_id IS NULL OR p_token IS NULL OR p_retry_safe IS NULL OR p_retry_delay_ms IS NULL OR p_retry_delay_ms NOT BETWEEN 1 AND 86400000 THEN RAISE EXCEPTION 'invalid confirm_external_no_effect arguments' USING ERRCODE='22023'; END IF;
    SELECT x.event_id INTO v_event FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF NOT FOUND THEN RETURN NULL; END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR d.state<>'RECONCILIATION_REQUIRED' OR d.target_code<>'VALIDATION_EXTERNAL_EFFECT' THEN RETURN NULL; END IF;
    SELECT x.* INTO c FROM vra.reconciliation_case x WHERE x.case_id=p_case_id AND x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR c.state<>'CHECKING' OR c.claim_token IS DISTINCT FROM p_token OR c.external_knowledge<>'UNKNOWN' THEN RETURN NULL; END IF;
    v_case_state := 'RESOLVED'; v_case_action := 'CONFIRMED_NO_EFFECT'; v_knowledge := 'CONFIRMED_NO_EFFECT';
    v_observation := 'CONFIRMED_NO_EFFECT'; v_reason := 'CONFIRMED_NO_EFFECT'; v_delivery_action := 'RECONCILED_NO_EFFECT';
    IF p_retry_safe AND d.cycle_claim_count<d.cycle_claim_limit THEN
        v_delivery_state := 'RETRY_WAIT'; v_class := 'RETRYABLE_TRANSIENT';
        v_delivery_due := pg_catalog.statement_timestamp()+p_retry_delay_ms*INTERVAL '1 millisecond';
    ELSE
        v_delivery_state := 'FAILED'; v_class := 'OPERATOR_REQUIRED';
    END IF;
    IF v_delivery_state IS NOT NULL THEN
        UPDATE vra.outbox_delivery x SET state=v_delivery_state,failure_class=v_class,
            reason_code=CASE WHEN v_delivery_state='SUCCEEDED' THEN NULL ELSE v_reason END,
            next_eligible_at=v_delivery_due,claim_token=NULL,claim_until=NULL,claim_relinquished=FALSE,
            state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=v_event AND x.state='RECONCILIATION_REQUIRED';
        IF NOT FOUND THEN RETURN NULL; END IF;
    END IF;
    UPDATE vra.reconciliation_case x SET state=v_case_state,external_knowledge=v_knowledge,reason_code=v_reason,
        next_eligible_at=v_due,claim_token=NULL,claim_until=NULL,state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.case_id=p_case_id AND x.state='CHECKING' AND x.claim_token=p_token;
    IF NOT FOUND THEN RAISE EXCEPTION 'locked case changed unexpectedly'; END IF;
    INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,observation,reason_code,actor_kind,recorded_at)
    SELECT x.case_id,v_case_action,c.state,x.state,p_token,x.lifetime_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,v_observation,x.reason_code,'RECONCILIATION_WORKER',pg_catalog.statement_timestamp()
    FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF v_delivery_state IS NOT NULL THEN
        INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,
            lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
        SELECT x.event_id,v_delivery_action,d.state,x.state,x.delivery_attempt_count,x.automatic_cycle,
            x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'RECONCILIATION_WORKER',pg_catalog.statement_timestamp()
        FROM vra.outbox_delivery x WHERE x.event_id=v_event;
    END IF;
    RETURN v_delivery_state;
END $$;
REVOKE ALL ON FUNCTION vra.async_confirm_external_no_effect(UUID,UUID,BOOLEAN,BIGINT) FROM PUBLIC;

CREATE FUNCTION vra.async_exhaust_reconciliation(p_case_id UUID,p_token UUID,p_reason_code VARCHAR) RETURNS BOOLEAN
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    d vra.outbox_delivery%ROWTYPE; c vra.reconciliation_case%ROWTYPE; v_event UUID;
    v_case_state VARCHAR; v_case_action VARCHAR; v_knowledge VARCHAR := 'UNKNOWN';
    v_delivery_state VARCHAR; v_delivery_action VARCHAR; v_reason VARCHAR; v_class VARCHAR;
    v_observation VARCHAR; v_due TIMESTAMPTZ; v_delivery_due TIMESTAMPTZ;
BEGIN
    IF p_case_id IS NULL OR p_token IS NULL OR p_reason_code IS NULL OR p_reason_code NOT IN ('RECONCILIATION_EXHAUSTED','OPERATOR_REQUIRED') THEN RAISE EXCEPTION 'invalid exhaust_reconciliation arguments' USING ERRCODE='22023'; END IF;
    SELECT x.event_id INTO v_event FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR d.state<>'RECONCILIATION_REQUIRED' OR d.target_code<>'VALIDATION_EXTERNAL_EFFECT' THEN RETURN FALSE; END IF;
    SELECT x.* INTO c FROM vra.reconciliation_case x WHERE x.case_id=p_case_id AND x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR c.state<>'CHECKING' OR c.claim_token IS DISTINCT FROM p_token OR c.external_knowledge<>'UNKNOWN' THEN RETURN FALSE; END IF;
    v_case_state := 'OPERATOR_REQUIRED'; v_case_action := 'EXHAUSTED'; v_reason := p_reason_code;
    v_delivery_state := 'FAILED'; v_delivery_action := 'RECONCILIATION_EXHAUSTED'; v_class := 'UNKNOWN_OUTCOME';
    IF v_delivery_state IS NOT NULL THEN
        UPDATE vra.outbox_delivery x SET state=v_delivery_state,failure_class=v_class,
            reason_code=CASE WHEN v_delivery_state='SUCCEEDED' THEN NULL ELSE v_reason END,
            next_eligible_at=v_delivery_due,claim_token=NULL,claim_until=NULL,claim_relinquished=FALSE,
            state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=v_event AND x.state='RECONCILIATION_REQUIRED';
        IF NOT FOUND THEN RETURN FALSE; END IF;
    END IF;
    UPDATE vra.reconciliation_case x SET state=v_case_state,external_knowledge=v_knowledge,reason_code=v_reason,
        next_eligible_at=v_due,claim_token=NULL,claim_until=NULL,state_changed_at=pg_catalog.statement_timestamp()
    WHERE x.case_id=p_case_id AND x.state='CHECKING' AND x.claim_token=p_token;
    IF NOT FOUND THEN RAISE EXCEPTION 'locked case changed unexpectedly'; END IF;
    INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,claim_token,
        lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,observation,reason_code,actor_kind,recorded_at)
    SELECT x.case_id,v_case_action,c.state,x.state,p_token,x.lifetime_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,v_observation,x.reason_code,'RECONCILIATION_WORKER',pg_catalog.statement_timestamp()
    FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF v_delivery_state IS NOT NULL THEN
        INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,
            lifetime_attempt,automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,recorded_at)
        SELECT x.event_id,v_delivery_action,d.state,x.state,x.delivery_attempt_count,x.automatic_cycle,
            x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,x.reason_code,'RECONCILIATION_WORKER',pg_catalog.statement_timestamp()
        FROM vra.outbox_delivery x WHERE x.event_id=v_event;
    END IF;
    RETURN TRUE;
END $$;
REVOKE ALL ON FUNCTION vra.async_exhaust_reconciliation(UUID,UUID,VARCHAR) FROM PUBLIC;

CREATE FUNCTION vra.async_control_replay(p_event_id UUID,p_reason_code VARCHAR,p_action_ref VARCHAR)
RETURNS BOOLEAN LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE d vra.outbox_delivery%ROWTYPE;
BEGIN
    IF p_event_id IS NULL OR p_reason_code IS NULL OR p_reason_code<>'CONTROLLED_REPLAY'
        OR p_action_ref IS NULL OR length(btrim(p_action_ref))=0 OR length(p_action_ref)>128 THEN
        RAISE EXCEPTION 'invalid replay arguments' USING ERRCODE='22023';
    END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR d.state<>'FAILED' OR d.failure_class='UNKNOWN_OUTCOME' OR EXISTS (
        SELECT 1 FROM vra.reconciliation_case c WHERE c.event_id=p_event_id
        AND c.state IN ('PENDING','CHECKING','WAITING','OPERATOR_REQUIRED')) THEN RETURN FALSE; END IF;
    UPDATE vra.outbox_delivery x SET state='READY',failure_class=NULL,reason_code=NULL,
        automatic_cycle=x.automatic_cycle+1,cycle_claim_count=0,cycle_claim_limit=5,
        state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=p_event_id AND x.state='FAILED';
    IF NOT FOUND THEN RETURN FALSE; END IF;
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,lifetime_attempt,
        automatic_cycle,cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,actor_ref,recorded_at)
    SELECT x.event_id,'CONTROL_REPLAY',d.state,x.state,x.delivery_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,p_reason_code,'ASYNC_OPERATOR',p_action_ref,pg_catalog.statement_timestamp()
    FROM vra.outbox_delivery x WHERE x.event_id=p_event_id;
    RETURN TRUE;
END $$;
REVOKE ALL ON FUNCTION vra.async_control_replay(UUID,VARCHAR,VARCHAR) FROM PUBLIC;

CREATE FUNCTION vra.async_control_resume(p_case_id UUID,p_reason_code VARCHAR,p_action_ref VARCHAR)
RETURNS BOOLEAN LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE d vra.outbox_delivery%ROWTYPE; c vra.reconciliation_case%ROWTYPE; v_event UUID;
BEGIN
    IF p_case_id IS NULL OR p_reason_code IS NULL OR p_reason_code<>'CONTROLLED_RESUME'
        OR p_action_ref IS NULL OR length(btrim(p_action_ref))=0 OR length(p_action_ref)>128 THEN
        RAISE EXCEPTION 'invalid resume arguments' USING ERRCODE='22023';
    END IF;
    SELECT x.event_id INTO v_event FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR d.state<>'FAILED' OR d.failure_class<>'UNKNOWN_OUTCOME' OR d.claim_token IS NOT NULL THEN RETURN FALSE; END IF;
    SELECT x.* INTO c FROM vra.reconciliation_case x WHERE x.case_id=p_case_id AND x.event_id=v_event FOR UPDATE;
    IF NOT FOUND OR c.state<>'OPERATOR_REQUIRED' OR c.external_knowledge<>'UNKNOWN' THEN RETURN FALSE; END IF;
    UPDATE vra.outbox_delivery x SET state='RECONCILIATION_REQUIRED',reason_code=p_reason_code,
        state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=v_event AND x.state='FAILED';
    IF NOT FOUND THEN RETURN FALSE; END IF;
    UPDATE vra.reconciliation_case x SET state='PENDING',reason_code=p_reason_code,
        automatic_cycle=x.automatic_cycle+1,cycle_claim_count=0,cycle_claim_limit=4,
        next_eligible_at=pg_catalog.statement_timestamp(),state_changed_at=pg_catalog.statement_timestamp()
        WHERE x.case_id=p_case_id AND x.state='OPERATOR_REQUIRED';
    IF NOT FOUND THEN RAISE EXCEPTION 'locked case changed unexpectedly'; END IF;
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,lifetime_attempt,
        automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,actor_ref,recorded_at)
    SELECT x.event_id,'CONTROL_RESUME',d.state,x.state,x.delivery_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,p_reason_code,'ASYNC_OPERATOR',p_action_ref,pg_catalog.statement_timestamp()
    FROM vra.outbox_delivery x WHERE x.event_id=v_event;
    INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,lifetime_attempt,
        automatic_cycle,cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,actor_ref,recorded_at)
    SELECT x.case_id,'CONTROL_RESUME',c.state,x.state,x.lifetime_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,p_reason_code,'ASYNC_OPERATOR',p_action_ref,pg_catalog.statement_timestamp()
    FROM vra.reconciliation_case x WHERE x.case_id=p_case_id;
    RETURN TRUE;
END $$;
REVOKE ALL ON FUNCTION vra.async_control_resume(UUID,VARCHAR,VARCHAR) FROM PUBLIC;

CREATE FUNCTION vra.async_control_close(p_event_id UUID,p_reason_code VARCHAR,p_action_ref VARCHAR)
RETURNS BOOLEAN LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE d vra.outbox_delivery%ROWTYPE; c vra.reconciliation_case%ROWTYPE; v_has_case BOOLEAN;
BEGIN
    IF p_event_id IS NULL OR p_reason_code IS NULL OR p_reason_code<>'CONTROLLED_CLOSE'
        OR p_action_ref IS NULL OR length(btrim(p_action_ref))=0 OR length(p_action_ref)>128 THEN
        RAISE EXCEPTION 'invalid close arguments' USING ERRCODE='22023';
    END IF;
    SELECT x.* INTO d FROM vra.outbox_delivery x WHERE x.event_id=p_event_id FOR UPDATE;
    IF NOT FOUND OR d.state<>'FAILED' THEN RETURN FALSE; END IF;
    SELECT x.* INTO c FROM vra.reconciliation_case x WHERE x.event_id=p_event_id
        AND x.state IN ('PENDING','CHECKING','WAITING','OPERATOR_REQUIRED') FOR UPDATE;
    v_has_case := FOUND;
    IF v_has_case AND (c.state<>'OPERATOR_REQUIRED' OR c.external_knowledge<>'UNKNOWN') THEN RETURN FALSE; END IF;
    UPDATE vra.outbox_delivery x SET state='CLOSED',reason_code=p_reason_code,
        state_changed_at=pg_catalog.statement_timestamp() WHERE x.event_id=p_event_id AND x.state='FAILED';
    IF NOT FOUND THEN RETURN FALSE; END IF;
    IF v_has_case THEN
        UPDATE vra.reconciliation_case x SET state='CLOSED',reason_code=p_reason_code,
            state_changed_at=pg_catalog.statement_timestamp() WHERE x.case_id=c.case_id AND x.state='OPERATOR_REQUIRED';
        IF NOT FOUND THEN RAISE EXCEPTION 'locked case changed unexpectedly'; END IF;
        INSERT INTO vra.reconciliation_history(case_id,action_code,from_state,to_state,lifetime_attempt,
            automatic_cycle,cycle_claim_count,cycle_claim_limit,reason_code,actor_kind,actor_ref,recorded_at)
        SELECT x.case_id,'CONTROL_CLOSE',c.state,x.state,x.lifetime_attempt_count,x.automatic_cycle,
            x.cycle_claim_count,x.cycle_claim_limit,p_reason_code,'ASYNC_OPERATOR',p_action_ref,pg_catalog.statement_timestamp()
        FROM vra.reconciliation_case x WHERE x.case_id=c.case_id;
    END IF;
    INSERT INTO vra.outbox_delivery_history(event_id,action_code,from_state,to_state,lifetime_attempt,
        automatic_cycle,cycle_claim_count,cycle_claim_limit,failure_class,reason_code,actor_kind,actor_ref,recorded_at)
    SELECT x.event_id,'CONTROL_CLOSE',d.state,x.state,x.delivery_attempt_count,x.automatic_cycle,
        x.cycle_claim_count,x.cycle_claim_limit,x.failure_class,p_reason_code,'ASYNC_OPERATOR',p_action_ref,pg_catalog.statement_timestamp()
    FROM vra.outbox_delivery x WHERE x.event_id=p_event_id;
    RETURN TRUE;
END $$;
REVOKE ALL ON FUNCTION vra.async_control_close(UUID,VARCHAR,VARCHAR) FROM PUBLIC;

CREATE FUNCTION vra.async_rebuild_reservation_projection() RETURNS TABLE(inserted BIGINT,repaired BIGINT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    LOCK TABLE vra.reservation_projection IN SHARE ROW EXCLUSIVE MODE;
    SELECT count(*) FILTER (WHERE p.reservation_id IS NULL),count(*) FILTER (WHERE p.reservation_id IS NOT NULL)
    INTO inserted,repaired FROM vra.inventory_reservation r LEFT JOIN vra.reservation_projection p USING(reservation_id);
    INSERT INTO vra.reservation_projection(reservation_id,sku_id,owner_id,location_id,stock_status,quantity,projected_at)
    SELECT r.reservation_id,r.sku_id,r.owner_id,r.location_id,r.stock_status,r.quantity,pg_catalog.statement_timestamp()
    FROM vra.inventory_reservation r
    ON CONFLICT (reservation_id) DO UPDATE SET sku_id=EXCLUDED.sku_id,owner_id=EXCLUDED.owner_id,
        location_id=EXCLUDED.location_id,stock_status=EXCLUDED.stock_status,quantity=EXCLUDED.quantity,projected_at=EXCLUDED.projected_at;
    RETURN NEXT;
END $$;
REVOKE ALL ON FUNCTION vra.async_rebuild_reservation_projection() FROM PUBLIC;

-- Bind while vra_owner still owns both each table and each helper.
CREATE TRIGGER outbox_event_immutable BEFORE UPDATE OR DELETE ON vra.outbox_event
    FOR EACH ROW EXECUTE FUNCTION vra.async_reject_immutable();
CREATE TRIGGER outbox_delivery_history_immutable BEFORE UPDATE OR DELETE ON vra.outbox_delivery_history
    FOR EACH ROW EXECUTE FUNCTION vra.async_reject_immutable();
CREATE TRIGGER reconciliation_history_immutable BEFORE UPDATE OR DELETE ON vra.reconciliation_history
    FOR EACH ROW EXECUTE FUNCTION vra.async_reject_immutable();
CREATE TRIGGER outbox_delivery_identity BEFORE UPDATE ON vra.outbox_delivery
    FOR EACH ROW EXECUTE FUNCTION vra.async_guard_delivery_identity();
CREATE TRIGGER reconciliation_case_identity BEFORE UPDATE ON vra.reconciliation_case
    FOR EACH ROW EXECUTE FUNCTION vra.async_guard_case_identity();
CREATE CONSTRAINT TRIGGER outbox_delivery_pair AFTER INSERT OR UPDATE OR DELETE ON vra.outbox_delivery
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION vra.async_check_pair();
CREATE CONSTRAINT TRIGGER reconciliation_case_pair AFTER INSERT OR UPDATE OR DELETE ON vra.reconciliation_case
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION vra.async_check_pair();
CREATE CONSTRAINT TRIGGER outbox_delivery_recovery AFTER INSERT OR UPDATE ON vra.outbox_delivery
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION vra.async_check_delivery_recovery();
CREATE CONSTRAINT TRIGGER reconciliation_case_recovery AFTER INSERT OR UPDATE ON vra.reconciliation_case
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION vra.async_check_case_recovery();

-- No stable schema CREATE. The SET membership itself is not revoked.
GRANT CREATE ON SCHEMA vra TO vra_async_executor;
ALTER FUNCTION vra.async_reject_immutable() OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_guard_delivery_identity() OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_guard_case_identity() OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_check_pair() OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_check_delivery_recovery() OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_check_case_recovery() OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_publish_reservation(UUID,UUID,VARCHAR) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_claim_delivery(VARCHAR,VARCHAR,INTEGER,BIGINT) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_relinquish_delivery(UUID,UUID) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_complete_delivery(UUID,UUID) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_retry_delivery(UUID,UUID,VARCHAR,BIGINT) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_fail_delivery(UUID,UUID,VARCHAR,VARCHAR) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_handoff_unknown(UUID,UUID,VARCHAR) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_claim_reconciliation(VARCHAR,INTEGER,BIGINT) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_wait_reconciliation(UUID,UUID,VARCHAR,BIGINT) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_confirm_external_success(UUID,UUID) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_confirm_external_no_effect(UUID,UUID,BOOLEAN,BIGINT) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_exhaust_reconciliation(UUID,UUID,VARCHAR) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_control_replay(UUID,VARCHAR,VARCHAR) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_control_resume(UUID,VARCHAR,VARCHAR) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_control_close(UUID,VARCHAR,VARCHAR) OWNER TO vra_async_executor;
ALTER FUNCTION vra.async_rebuild_reservation_projection() OWNER TO vra_async_executor;
REVOKE CREATE ON SCHEMA vra FROM vra_async_executor;

GRANT USAGE ON SCHEMA vra TO vra_outbox_worker,vra_reconciliation_worker,vra_async_operator,
    vra_projection_rebuilder,vra_async_observer,vra_async_executor;
GRANT SELECT ON vra.outbox_event,vra.outbox_delivery,vra.outbox_delivery_history TO vra_outbox_worker;
GRANT SELECT,INSERT ON vra.consumer_inbox,vra.reservation_projection TO vra_outbox_worker;
GRANT SELECT ON vra.outbox_event,vra.outbox_delivery,vra.reconciliation_case,vra.reconciliation_history TO vra_reconciliation_worker;
GRANT SELECT ON vra.outbox_event,vra.outbox_delivery,vra.reconciliation_case,vra.outbox_delivery_history,vra.reconciliation_history TO vra_async_operator;
GRANT SELECT ON vra.inventory_reservation,vra.reservation_projection TO vra_projection_rebuilder;
GRANT SELECT ON vra.outbox_delivery,vra.reconciliation_case,vra.outbox_delivery_history,vra.reconciliation_history TO vra_async_observer;
GRANT SELECT ON vra.inventory_reservation,vra.outbox_event,vra.outbox_delivery,vra.reconciliation_case,
    vra.reservation_projection,vra.outbox_delivery_history,vra.reconciliation_history TO vra_async_executor;
GRANT INSERT ON vra.outbox_event,vra.outbox_delivery,vra.outbox_delivery_history,
    vra.reconciliation_case,vra.reconciliation_history,vra.reservation_projection TO vra_async_executor;
GRANT UPDATE ON vra.outbox_delivery,vra.reconciliation_case,vra.reservation_projection TO vra_async_executor;
GRANT USAGE ON SEQUENCE vra.outbox_delivery_history_history_id_seq,vra.reconciliation_history_history_id_seq TO vra_async_executor;

-- Non-inheriting administrative path: grant as the new function owner.
-- This is migration SQL, never a SET ROLE in a SECURITY DEFINER body.
SET LOCAL ROLE vra_async_executor;
GRANT EXECUTE ON FUNCTION vra.async_publish_reservation(UUID,UUID,VARCHAR) TO vra_runtime;
GRANT EXECUTE ON FUNCTION vra.async_claim_delivery(VARCHAR,VARCHAR,INTEGER,BIGINT),
    vra.async_relinquish_delivery(UUID,UUID),vra.async_complete_delivery(UUID,UUID),
    vra.async_retry_delivery(UUID,UUID,VARCHAR,BIGINT),vra.async_fail_delivery(UUID,UUID,VARCHAR,VARCHAR),
    vra.async_handoff_unknown(UUID,UUID,VARCHAR) TO vra_outbox_worker;
GRANT EXECUTE ON FUNCTION vra.async_claim_reconciliation(VARCHAR,INTEGER,BIGINT),
    vra.async_wait_reconciliation(UUID,UUID,VARCHAR,BIGINT),vra.async_confirm_external_success(UUID,UUID),
    vra.async_confirm_external_no_effect(UUID,UUID,BOOLEAN,BIGINT),vra.async_exhaust_reconciliation(UUID,UUID,VARCHAR)
    TO vra_reconciliation_worker;
GRANT EXECUTE ON FUNCTION vra.async_control_replay(UUID,VARCHAR,VARCHAR),
    vra.async_control_resume(UUID,VARCHAR,VARCHAR),vra.async_control_close(UUID,VARCHAR,VARCHAR) TO vra_async_operator;
GRANT EXECUTE ON FUNCTION vra.async_rebuild_reservation_projection() TO vra_projection_rebuilder;
SET LOCAL ROLE vra_owner;

DO $$
BEGIN
    IF pg_catalog.has_schema_privilege('vra_async_executor','vra','CREATE') THEN
        RAISE EXCEPTION 'executor retained schema CREATE';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='vra' AND c.relowner='vra_async_executor'::regrole) THEN
        RAISE EXCEPTION 'executor owns a relation';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='vra' AND p.proname LIKE 'async_%'
        AND (p.proowner<>'vra_async_executor'::regrole OR NOT p.prosecdef
             OR p.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog, pg_temp']
             OR EXISTS (SELECT 1 FROM pg_catalog.aclexplode(p.proacl) a WHERE a.grantee=0 AND a.privilege_type='EXECUTE'))) THEN
        RAISE EXCEPTION 'async function security boundary mismatch';
    END IF;
END $$;
