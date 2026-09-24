package dev.vra.migration;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Logger;

final class OwnerRoleDataSource implements DataSource {

    private static final String OWNER_ROLE_SQL = "SET ROLE vra_owner";

    private final String url;
    private final String username;
    private final String password;

    OwnerRoleDataSource(String url, String username, String password) {
        this.url = requireNonBlank(url, "url");
        this.username = requireNonBlank(username, "username");
        this.password = requireNonBlank(password, "password");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return open(username, password);
    }

    @Override
    public Connection getConnection(String connectionUsername, String connectionPassword)
            throws SQLException {
        return open(connectionUsername, connectionPassword);
    }

    private Connection open(String connectionUsername, String connectionPassword)
            throws SQLException {
        Connection connection =
                DriverManager.getConnection(url, connectionUsername, connectionPassword);

        boolean ready = false;

        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute(OWNER_ROLE_SQL);
            }

            ready = true;
            return connection;
        } finally {
            if (!ready) {
                connection.close();
            }
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return DriverManager.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        DriverManager.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger(OwnerRoleDataSource.class.getName());
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        Objects.requireNonNull(iface, "iface");

        if (iface.isInstance(this)) {
            return iface.cast(this);
        }

        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface != null && iface.isInstance(this);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        return value;
    }
}
