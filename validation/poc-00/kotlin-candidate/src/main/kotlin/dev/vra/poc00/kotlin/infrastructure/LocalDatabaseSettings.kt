package dev.vra.poc00.kotlin.infrastructure

/** Local POC connection settings. Never log this object or its password. */
class LocalDatabaseSettings(url: String, username: String, password: String?) {
    val url: String
    val username: String
    val password: String

    init {
        if (password.isNullOrBlank()) {
            throw IllegalStateException("VRA_DB_PASSWORD is required; load the local secret before running.")
        }
        require(Regex("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/vra_poc00").matches(url)) {
            "POC database URL must target loopback and database vra_poc00."
        }
        require(username == "vra_poc00") { "POC database username must be vra_poc00." }
        this.url = url
        this.username = username
        this.password = password
    }

    companion object {
        const val DEFAULT_URL = "jdbc:postgresql://127.0.0.1:55432/vra_poc00"

        fun fromEnvironment(environment: Map<String, String> = System.getenv()) =
            LocalDatabaseSettings(
                environment["VRA_DB_URL"] ?: DEFAULT_URL,
                environment["VRA_DB_USERNAME"] ?: "vra_poc00",
                environment["VRA_DB_PASSWORD"]
            )
    }
}
