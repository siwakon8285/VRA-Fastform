CREATE TABLE inventory_balance (
    sku_id uuid PRIMARY KEY,
    on_hand bigint NOT NULL CHECK (on_hand >= 0),
    reserved bigint NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (reserved <= on_hand)
);

CREATE TABLE orders (
    id uuid PRIMARY KEY,
    state varchar(32) NOT NULL CHECK (state IN ('CREATED', 'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED')),
    created_at timestamptz NOT NULL,
    confirmed_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK ((state = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CHECK (confirmed_at IS NULL OR confirmed_at >= created_at)
);

CREATE TABLE order_items (
    order_id uuid NOT NULL REFERENCES orders(id),
    line_number integer NOT NULL CHECK (line_number > 0),
    sku_id uuid NOT NULL,
    product_name_snapshot text NOT NULL CHECK (length(btrim(product_name_snapshot)) > 0),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric NOT NULL CHECK (unit_price >= 0 AND unit_price <> 'NaN'::numeric
        AND unit_price <> 'Infinity'::numeric),
    currency varchar(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    PRIMARY KEY (order_id, line_number)
);
