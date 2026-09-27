package dev.vra.async;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Adds the committed Stage A role prerequisite to disposable runtime databases. */
public final class AsyncRoleBootstrap {
    private AsyncRoleBootstrap() {
    }

    public static void run(PostgreSQLContainer postgres) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            for (String role : new String[] {
                    "vra_outbox_worker", "vra_reconciliation_worker", "vra_async_operator",
                    "vra_projection_rebuilder", "vra_async_observer"}) {
                statement.execute("CREATE ROLE " + role + " LOGIN NOINHERIT NOSUPERUSER "
                        + "NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS "
                        + "PASSWORD 'async-disposable-test-only'");
            }
            statement.execute("CREATE ROLE vra_async_executor NOLOGIN NOINHERIT NOSUPERUSER "
                    + "NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS");
            statement.execute("GRANT vra_async_executor TO vra_owner "
                    + "WITH SET TRUE, INHERIT FALSE, ADMIN FALSE");
            statement.execute("GRANT CONNECT ON DATABASE " + quote(postgres.getDatabaseName())
                    + " TO vra_outbox_worker, vra_reconciliation_worker, vra_async_operator, "
                    + "vra_projection_rebuilder, vra_async_observer");
            statement.execute("GRANT USAGE ON SCHEMA vra TO vra_outbox_worker, "
                    + "vra_reconciliation_worker, vra_async_operator, vra_projection_rebuilder, "
                    + "vra_async_observer, vra_async_executor");
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
