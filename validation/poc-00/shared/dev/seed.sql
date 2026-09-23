-- Synthetic only. Re-running never resets existing reservations.
INSERT INTO inventory_balance (sku_id, on_hand, reserved)
VALUES ('00000000-0000-0000-0000-000000000001', 1000000, 0),
       ('00000000-0000-0000-0000-000000000002', 1, 0)
ON CONFLICT (sku_id) DO NOTHING;
