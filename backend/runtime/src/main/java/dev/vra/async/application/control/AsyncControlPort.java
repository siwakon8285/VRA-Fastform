package dev.vra.async.application.control;

import java.util.UUID;

/** One-event controlled replay only; resume and close belong to later work. */
public interface AsyncControlPort {
    boolean replay(UUID eventId, String actionRef);
}
