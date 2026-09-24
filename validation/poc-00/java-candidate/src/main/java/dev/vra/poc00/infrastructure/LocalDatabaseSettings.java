package dev.vra.poc00.infrastructure;

/** Deliberately restricted to this local experiment. Never print this object or its password. */
public final class LocalDatabaseSettings {
    public static final String DEFAULT_URL = "jdbc:postgresql://127.0.0.1:55432/vra_poc00";
    private final String url;
    private final String username;
    private final String password;

    public LocalDatabaseSettings(String url, String username, String password) {
        if (password == null || password.isBlank())
            throw new IllegalStateException("VRA_DB_PASSWORD is required; load the local secret before running.");
        if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/vra_poc00"))
            throw new IllegalArgumentException("POC database URL must target loopback and database vra_poc00.");
        if (!"vra_poc00".equals(username))
            throw new IllegalArgumentException("POC database username must be vra_poc00.");
        this.url = url;
        this.username = username;
        this.password = password;
    }
    public static LocalDatabaseSettings fromEnvironment() {
        var env = System.getenv();
        return new LocalDatabaseSettings(env.getOrDefault("VRA_DB_URL", DEFAULT_URL),
                env.getOrDefault("VRA_DB_USERNAME", "vra_poc00"), env.get("VRA_DB_PASSWORD"));
    }
    public String url() { return url; }
    public String username() { return username; }
    public String password() { return password; }
}
