package dev.vra.migration;

import org.flywaydb.core.Flyway;

public final class MigrationRunner {

    public int migrate(String url, String username, String password) {
        Flyway flyway = configuredFlyway(url, username, password);
        var result = flyway.migrate();
        flyway.validate();
        return result.migrationsExecuted;
    }

    public void validate(String url, String username, String password) {
        configuredFlyway(url, username, password).validate();
    }

    private Flyway configuredFlyway(String url, String username, String password) {
        return Flyway.configure()
                .dataSource(new OwnerRoleDataSource(url, username, password))
                .locations("classpath:db/migration")
                .schemas("vra")
                .defaultSchema("vra")
                .createSchemas(false)
                .load();
    }
}
