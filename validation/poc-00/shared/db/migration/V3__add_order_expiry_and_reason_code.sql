-- Preserve existing states while permitting the additive EXPIRED state.
ALTER TABLE orders DROP CONSTRAINT orders_state_check;
ALTER TABLE orders ADD CONSTRAINT orders_state_valid_check CHECK (
    state IN ('CREATED', 'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')
);

ALTER TABLE orders ADD COLUMN reason_code varchar(32);

-- CASE returns a strict boolean per state. EXPIRED explicitly requires a non-NULL
-- PAYMENT_TIMEOUT so PostgreSQL CHECK's acceptance of NULL cannot bypass the rule.
ALTER TABLE orders ADD CONSTRAINT orders_reason_code_valid_check CHECK (
    CASE state
        WHEN 'CREATED' THEN reason_code IS NULL
        WHEN 'PENDING_PAYMENT' THEN reason_code IS NULL
        WHEN 'CONFIRMED' THEN reason_code IS NULL
        WHEN 'CANCELLED' THEN reason_code IS NULL
            OR reason_code IN ('CUSTOMER_CANCELLED', 'PAYMENT_FAILED')
        WHEN 'EXPIRED' THEN reason_code IS NOT NULL AND reason_code = 'PAYMENT_TIMEOUT'
        ELSE FALSE
    END
);
