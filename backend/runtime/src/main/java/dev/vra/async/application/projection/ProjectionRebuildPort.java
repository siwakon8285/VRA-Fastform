package dev.vra.async.application.projection;

/** One controlled rebuild of the derived reservation projection. */
public interface ProjectionRebuildPort {
    /** V3 counts absent rows as inserted and all previously present rows as repaired. */
    record RebuildResult(long inserted, long repaired) {
        public RebuildResult {
            if (inserted < 0 || repaired < 0) {
                throw new IllegalArgumentException("Rebuild counts must be nonnegative");
            }
        }
    }

    RebuildResult rebuild();
}
