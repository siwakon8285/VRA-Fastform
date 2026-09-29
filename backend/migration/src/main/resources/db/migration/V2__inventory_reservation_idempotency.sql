CREATE TABLE vra.inventory_reservation_idempotency (
    actor_scope VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    fingerprint_version SMALLINT NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,

    outcome_status VARCHAR(16) NULL,
    reservation_id UUID NULL,
    inventory_version BIGINT NULL,
    rejection_code VARCHAR(64) NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NULL,

    CONSTRAINT inventory_reservation_idempotency_pk
        PRIMARY KEY (actor_scope, idempotency_key),

    CONSTRAINT inventory_reservation_idempotency_fingerprint_version_ck
        CHECK (fingerprint_version = 1),

    CONSTRAINT inventory_reservation_idempotency_fingerprint_ck
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),

    CONSTRAINT inventory_reservation_idempotency_outcome_ck
        CHECK (
            outcome_status IS NULL
            OR outcome_status IN ('SUCCEEDED', 'REJECTED')
        ),

    CONSTRAINT inventory_reservation_idempotency_shape_ck
        CHECK (
            (
                outcome_status IS NULL
                AND reservation_id IS NULL
                AND inventory_version IS NULL
                AND rejection_code IS NULL
                AND completed_at IS NULL
            )
            OR (
                outcome_status IS NOT NULL
                AND outcome_status = 'SUCCEEDED'
                AND reservation_id IS NOT NULL
                AND inventory_version IS NOT NULL
                AND rejection_code IS NULL
                AND completed_at IS NOT NULL
            )
            OR (
                outcome_status IS NOT NULL
                AND outcome_status = 'REJECTED'
                AND reservation_id IS NULL
                AND inventory_version IS NULL
                AND rejection_code IS NOT NULL
                AND completed_at IS NOT NULL
            )
        ),

    CONSTRAINT inventory_reservation_idempotency_reservation_fk
        FOREIGN KEY (reservation_id)
        REFERENCES vra.inventory_reservation (reservation_id)
);

GRANT SELECT, INSERT, UPDATE
    ON TABLE vra.inventory_reservation_idempotency
    TO vra_runtime;
