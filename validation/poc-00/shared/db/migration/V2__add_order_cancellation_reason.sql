-- Additive: legacy CANCELLED rows remain valid without a reason.
ALTER TABLE orders ADD COLUMN cancellation_reason text;
ALTER TABLE orders ADD CONSTRAINT cancellation_reason_valid CHECK (
    cancellation_reason IS NULL OR
    (state = 'CANCELLED' AND length(btrim(cancellation_reason)) BETWEEN 1 AND 500)
);
