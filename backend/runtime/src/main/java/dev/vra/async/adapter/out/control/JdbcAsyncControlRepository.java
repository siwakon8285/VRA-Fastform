package dev.vra.async.adapter.out.control;

import java.util.Objects;
import java.util.UUID;

import dev.vra.async.application.control.AsyncControlPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Operator-only guarded replay; no table DML or batch control surface. */
@Repository
public class JdbcAsyncControlRepository implements AsyncControlPort {
    private final JdbcClient jdbc;

    public JdbcAsyncControlRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean replay(UUID eventId, String actionRef) {
        Objects.requireNonNull(eventId, "eventId");
        if (actionRef == null || actionRef.isBlank() || actionRef.length() > 128) {
            throw new IllegalArgumentException("Action reference must be 1..128 nonblank characters");
        }
        return jdbc.sql("SELECT vra.async_control_replay(:event,'CONTROLLED_REPLAY',:ref)")
                .param("event", eventId).param("ref", actionRef)
                .query(Boolean.class).single();
    }
}
