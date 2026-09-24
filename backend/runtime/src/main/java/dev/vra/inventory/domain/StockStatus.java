package dev.vra.inventory.domain;

public enum StockStatus {
    AVAILABLE(true),
    QUARANTINED(false);

    private final boolean reservable;

    StockStatus(boolean reservable) {
        this.reservable = reservable;
    }

    public boolean isReservable() {
        return reservable;
    }
}
