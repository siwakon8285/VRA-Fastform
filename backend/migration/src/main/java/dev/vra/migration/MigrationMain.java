package dev.vra.migration;

public final class MigrationMain {

    private MigrationMain() {
    }

    public static void main(String[] args) {
        String url = requiredEnvironment("VRA_MIGRATION_DB_URL");
        String username = requiredEnvironment("VRA_MIGRATION_DB_USERNAME");
        String password = requiredEnvironment("VRA_MIGRATION_DB_PASSWORD");

        int migrationsExecuted =
                new MigrationRunner().migrate(url, username, password);

        System.out.printf(
                "Database migration completed successfully; migrations executed: %d%n",
                migrationsExecuted
        );
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable is missing or blank: " + name
            );
        }

        return value;
    }
}
