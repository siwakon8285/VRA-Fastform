CREATE TABLE vra.inventory_balance (
    sku_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    location_id UUID NOT NULL,
    stock_status VARCHAR(32) NOT NULL,
    on_hand BIGINT NOT NULL,
    reserved BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT inventory_balance_pk
        PRIMARY KEY (sku_id, owner_id, location_id, stock_status),

    CONSTRAINT inventory_balance_status_ck
        CHECK (stock_status IN ('AVAILABLE', 'QUARANTINED')),

    CONSTRAINT inventory_balance_on_hand_ck
        CHECK (on_hand >= 0),

    CONSTRAINT inventory_balance_reserved_ck
        CHECK (reserved >= 0),

    CONSTRAINT inventory_balance_reserved_not_over_on_hand_ck
        CHECK (reserved <= on_hand),

    CONSTRAINT inventory_balance_version_ck
        CHECK (version >= 0)
);

CREATE TABLE vra.inventory_reservation (
    reservation_id UUID NOT NULL,
    sku_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    location_id UUID NOT NULL,
    stock_status VARCHAR(32) NOT NULL,
    quantity BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT inventory_reservation_pk
        PRIMARY KEY (reservation_id),

    CONSTRAINT inventory_reservation_quantity_ck
        CHECK (quantity > 0),

    CONSTRAINT inventory_reservation_balance_fk
        FOREIGN KEY (sku_id, owner_id, location_id, stock_status)
        REFERENCES vra.inventory_balance (
            sku_id,
            owner_id,
            location_id,
            stock_status
        )
);

GRANT SELECT, UPDATE
    ON TABLE vra.inventory_balance
    TO vra_runtime;

GRANT SELECT, INSERT
    ON TABLE vra.inventory_reservation
    TO vra_runtime;
