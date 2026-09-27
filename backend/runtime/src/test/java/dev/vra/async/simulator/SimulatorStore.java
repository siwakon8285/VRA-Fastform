package dev.vra.async.simulator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.UUID;

import dev.vra.async.contract.ReservationCreatedEventV1.Payload;

/** Test-only durable authority, independent of VRA's database. */
public final class SimulatorStore {
    public enum Registration { NEW, PENDING, SUCCEEDED, CONFLICT }
    public record Snapshot(UUID operationId, String digest, String state, String result, int effectCount) {}

    private final String url;
    private final String user;
    private final String password;

    public SimulatorStore(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public void initialize() throws SQLException {
        try (Connection db = connect(); var statement = db.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS public.sim_operation ("
                    + "operation_id UUID PRIMARY KEY, semantic_payload_digest CHAR(64) NOT NULL, "
                    + "state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING','SUCCEEDED')), "
                    + "result_code VARCHAR(32), effect_count INTEGER NOT NULL DEFAULT 0, "
                    + "CHECK ((state='PENDING' AND result_code IS NULL AND effect_count=0) OR "
                    + "(state='SUCCEEDED' AND result_code='SUCCEEDED' AND effect_count=1)))");
            statement.execute("CREATE TABLE IF NOT EXISTS public.sim_request_history ("
                    + "request_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,"
                    + "operation_id UUID NOT NULL,kind VARCHAR(8) NOT NULL "
                    + "CHECK (kind IN ('EXECUTE','OBSERVE')))");
        }
    }

    /** Test-only durable ordering evidence; never used for effect or absence decisions. */
    public void recordRequest(UUID operationId, String kind) throws SQLException {
        try (Connection db = connect(); PreparedStatement insert = db.prepareStatement(
                "INSERT INTO public.sim_request_history(operation_id,kind) VALUES (?,?)")) {
            insert.setObject(1, operationId);
            insert.setString(2, kind);
            insert.executeUpdate();
        }
    }

    public Registration register(UUID operationId, Payload payload) throws SQLException {
        String digest = digest(payload);
        try (Connection db = connect(); PreparedStatement insert = db.prepareStatement(
                "INSERT INTO public.sim_operation(operation_id,semantic_payload_digest,state,effect_count) "
                        + "VALUES (?,?,'PENDING',0) ON CONFLICT DO NOTHING")) {
            insert.setObject(1, operationId);
            insert.setString(2, digest);
            if (insert.executeUpdate() == 1) return Registration.NEW;
        }
        Snapshot existing = read(operationId);
        if (existing == null) throw new IllegalStateException("Registered operation disappeared");
        if (!existing.digest().equals(digest)) return Registration.CONFLICT;
        return existing.state().equals("SUCCEEDED") ? Registration.SUCCEEDED : Registration.PENDING;
    }

    public Snapshot read(UUID operationId) throws SQLException {
        try (Connection db = connect(); PreparedStatement query = db.prepareStatement(
                "SELECT operation_id,semantic_payload_digest,state,result_code,effect_count "
                        + "FROM public.sim_operation WHERE operation_id=?")) {
            query.setObject(1, operationId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                return new Snapshot(row.getObject(1, UUID.class), row.getString(2).trim(),
                        row.getString(3), row.getString(4), row.getInt(5));
            }
        }
    }

    public Snapshot complete(UUID operationId) throws SQLException {
        try (Connection db = connect(); PreparedStatement update = db.prepareStatement(
                "UPDATE public.sim_operation SET state='SUCCEEDED',result_code='SUCCEEDED',effect_count=1 "
                        + "WHERE operation_id=? AND state='PENDING'")) {
            update.setObject(1, operationId);
            update.executeUpdate();
        }
        Snapshot result = read(operationId);
        if (result == null || !result.state().equals("SUCCEEDED") || result.effectCount() != 1) {
            throw new IllegalStateException("Simulator effect did not commit");
        }
        return result;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public static String digest(Payload payload) {
        String canonical = String.join("\n", payload.reservationId().toString(), payload.skuId().toString(),
                payload.ownerId().toString(), payload.locationId().toString(),
                payload.stockStatus().name(), Long.toString(payload.quantity()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
