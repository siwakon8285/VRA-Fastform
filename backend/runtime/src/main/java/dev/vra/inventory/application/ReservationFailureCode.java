package dev.vra.inventory.application;

public enum ReservationFailureCode {
    INVALID_QUANTITY,
    INVENTORY_NOT_FOUND,
    INVENTORY_NOT_RESERVABLE,
    INSUFFICIENT_STOCK,
    VERSION_CONFLICT
}
