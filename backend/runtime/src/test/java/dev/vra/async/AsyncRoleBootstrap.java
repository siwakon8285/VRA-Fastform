package dev.vra.async;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Adds the async and security-foundation role prerequisites to disposable databases. */
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
            statement.execute(securityBootstrapSql());
        }
    }

    private static String securityBootstrapSql() throws SQLException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(
                root.resolve("validation/poc-04/db/bootstrap-security-roles.sql"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new SQLException("security-foundation bootstrap is required");
        }
        try {
            return Files.readString(root.resolve("validation/poc-04/db/bootstrap-security-roles.sql"));
        } catch (IOException failure) {
            throw new SQLException("cannot read security-foundation bootstrap", failure);
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
