package dev.vra.async.application.control;

import java.util.UUID;

/** One-event guarded operator controls; no generic state or batch operation. */
public interface AsyncControlPort {
    boolean replay(UUID eventId, String actionRef);

    boolean resume(UUID caseId, String actionRef);

    boolean close(UUID eventId, String actionRef);
}
