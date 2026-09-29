package dev.vra.async.application.projection;

import java.util.Objects;
import java.util.UUID;

import dev.vra.async.contract.ReservationCreatedEventV1;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Scenario A: one durable inbox identity and its projection effect per transaction. */
@Service
public class ReservationProjectionConsumer {
    public static final String CONSUMER_IDENTITY = "reservation_projection_v1";

    public enum Outcome { APPLIED, ALREADY_APPLIED }

    public interface ConsumerInbox {
        boolean register(UUID eventId);
        boolean contains(UUID eventId);
    }

    public interface ReservationProjection {
        void insertOrVerify(Payload payload);
    }

    private final ConsumerInbox inbox;
    private final ReservationProjection projection;

    public ReservationProjectionConsumer(ConsumerInbox inbox, ReservationProjection projection) {
        this.inbox = Objects.requireNonNull(inbox);
        this.projection = Objects.requireNonNull(projection);
    }

    /** Commits independently of the preceding claim and subsequent guarded finalize. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Outcome consume(ReservationCreatedEventV1 event) {
        ReservationCreatedEventV1 valid = validate(event);
        if (!inbox.register(valid.eventId())) {
            if (!inbox.contains(valid.eventId())) {
                throw new IllegalStateException("Inbox conflict without durable prior processing");
            }
            return Outcome.ALREADY_APPLIED;
        }
        projection.insertOrVerify(valid.payload());
        return Outcome.APPLIED;
    }

    private static ReservationCreatedEventV1 validate(ReservationCreatedEventV1 event) {
        Objects.requireNonNull(event, "event");
        Payload payload = Objects.requireNonNull(event.payload(), "payload");
        return new ReservationCreatedEventV1(event.eventId(), event.eventType(),
                event.schemaVersion(), event.occurredAt(), new Payload(
                payload.reservationId(), payload.skuId(), payload.ownerId(),
                payload.locationId(), payload.stockStatus(), payload.quantity()));
    }
}
