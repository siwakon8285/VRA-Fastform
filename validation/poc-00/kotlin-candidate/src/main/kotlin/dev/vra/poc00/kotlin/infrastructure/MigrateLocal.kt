package dev.vra.poc00.kotlin.infrastructure

import org.flywaydb.core.Flyway

/** Explicit migration process; the web application never runs this automatically. */
object MigrateLocal {
    @JvmStatic
    fun main(args: Array<String>) {
        val settings = LocalDatabaseSettings.fromEnvironment()
        Flyway.configure()
            .dataSource(settings.url, settings.username, settings.password)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .load()
            .migrate()
    }
}
