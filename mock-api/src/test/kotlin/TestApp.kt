package com.maskinporten

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.io.File
import java.sql.DriverManager

/**
 * Tests run against a dedicated `maskinporten-test` database in the docker-compose Postgres
 * (`docker-compose up -d db`), never the dev database. It is wiped and reseeded from init.sql
 * before every test so tests cannot affect each other.
 */
private object TestDatabase {
    private val host = System.getenv("DB_HOST") ?: "localhost"
    private val port = System.getenv("DB_PORT") ?: "5433"
    val user = System.getenv("DB_USER") ?: "admin"
    val password = System.getenv("DB_PASSWORD") ?: "admin"
    val name = "maskinporten-test"
    private val initSql = File("init.sql").readText()

    init {
        DriverManager.getConnection("jdbc:postgresql://$host:$port/postgres", user, password).use { conn ->
            val exists = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?").use {
                it.setString(1, name)
                it.executeQuery().next()
            }
            if (!exists) conn.createStatement().use { it.execute("CREATE DATABASE \"$name\"") }
        }
    }

    fun reset() {
        DriverManager.getConnection("jdbc:postgresql://$host:$port/$name", user, password).use { conn ->
            conn.createStatement().use {
                it.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
                it.execute(initSql)
            }
        }
    }

    val settings = mapOf(
        "ktor.database.host" to host,
        "ktor.database.port" to port,
        "ktor.database.name" to name,
        "ktor.database.user" to user,
        "ktor.database.password" to password,
    )
}

/** Maskinporten is off, so tests need no CA certificate. MaskinportenTest turns it on. */
fun testApp(
    maskinporten: Map<String, String> = mapOf("maskinporten.enabled" to "false"),
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    TestDatabase.reset()
    environment {
        config = MapApplicationConfig(*(TestDatabase.settings + maskinporten).toList().toTypedArray())
    }
    application { module() }
    block()
}
