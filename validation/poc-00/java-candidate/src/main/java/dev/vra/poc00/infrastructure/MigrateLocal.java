package dev.vra.poc00.infrastructure;

import org.flywaydb.core.Flyway;

/** Separate process; the running backend never invokes this command. */
public final class MigrateLocal {
    private MigrateLocal() {}
    public static void main(String[] args) {
        var settings = LocalDatabaseSettings.fromEnvironment();
        Flyway.configure().dataSource(settings.url(), settings.username(), settings.password())
                .locations("classpath:db/migration").cleanDisabled(true)
                .load().migrate();
    }
}
