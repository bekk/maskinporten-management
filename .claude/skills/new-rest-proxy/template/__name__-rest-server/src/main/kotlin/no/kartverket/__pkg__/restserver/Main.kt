package no.kartverket.__pkg__.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import no.kartverket.__pkg__.client.__Name__Client

fun main() {
    val config = loadApplicationConfig()
    embeddedServer(
        Netty,
        environment = applicationEnvironment { this.config = config },
        configure = { connector { port = config.port("server.port") } },
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    // TODO: Logging goes first, as configureLogging(), once the team has settled how we log
    configureSerialization()
    configureErrorHandling()
    configure__Name__()
    configureRouting()
}

@OptIn(ExperimentalKtorApi::class)
fun Application.configureRouting() {
    val spec: String by lazy { openApiSpec() }

    routing {
        get("/health/live") {
            call.respond(HttpStatusCode.OK)
        }.hide()

        get("/openapi") {
            call.respondText(spec, ContentType.Application.Json)
        }.hide()

        // TODO: Replace with the real endpoints
        get("/api/example-items/{id}") {
            val client: __Name__Client by dependencies
            call.respond(client.getExampleItem(call.pathParameters["id"].orEmpty()))
        }.describe(exampleItemOperation)
    }
}
