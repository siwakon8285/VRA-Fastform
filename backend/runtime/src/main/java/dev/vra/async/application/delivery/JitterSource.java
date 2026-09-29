package dev.vra.async.application.delivery;

/** Supplies a unit value in [-1, 1]; tests inject exact values. */
@FunctionalInterface
public interface JitterSource {
    double sampleUnit();
}
