package no.kartverket.__pkg__.restserver

import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        // Leaves out fields that are null rather than sending them, and accepts fields this API does not know
        json(
            Json(DefaultJson) {
                explicitNulls = false
                ignoreUnknownKeys = true
            },
        )
    }
}
