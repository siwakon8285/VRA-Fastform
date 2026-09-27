package dev.vra.async.adapter.out.projection;

import java.util.Objects;

import dev.vra.async.application.projection.ProjectionRebuildPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Invoked only by the bounded POC caller using vra_projection_rebuilder. */
public final class JdbcProjectionRebuilder implements ProjectionRebuildPort {
    private final JdbcClient jdbc;

    public JdbcProjectionRebuilder(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RebuildResult rebuild() {
        return jdbc.sql("SELECT inserted,repaired FROM vra.async_rebuild_reservation_projection()")
                .query((row, number) -> new RebuildResult(row.getLong("inserted"), row.getLong("repaired")))
                .single();
    }
}
