package dev.vra.async.adapter.out.projection;

import java.util.Objects;
import java.util.UUID;

import dev.vra.async.application.projection.ReservationProjectionConsumer.ConsumerInbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import static dev.vra.async.application.projection.ReservationProjectionConsumer.CONSUMER_IDENTITY;

/** PostgreSQL unique identity is the deduplication authority. */
@Repository
public class JdbcConsumerInbox implements ConsumerInbox {
    private final JdbcClient jdbc;

    public JdbcConsumerInbox(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public boolean register(UUID eventId) {
        return jdbc.sql("INSERT INTO vra.consumer_inbox(consumer_identity,event_id,processed_at) "
                        + "VALUES (:consumer,:event,statement_timestamp()) "
                        + "ON CONFLICT DO NOTHING RETURNING event_id")
                .param("consumer", CONSUMER_IDENTITY)
                .param("event", Objects.requireNonNull(eventId))
                .query(UUID.class).optional().isPresent();
    }

    @Override
    public boolean contains(UUID eventId) {
        return jdbc.sql("SELECT 1 FROM vra.consumer_inbox WHERE consumer_identity=:consumer AND event_id=:event")
                .param("consumer", CONSUMER_IDENTITY)
                .param("event", Objects.requireNonNull(eventId))
                .query(Integer.class).optional().isPresent();
    }
}
