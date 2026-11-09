package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.restserver.config.AppConfig
import no.kartverket.maskinportenmanagement.restserver.config.DigdirConfig
import no.kartverket.maskinportenmanagement.restserver.config.externalFiltering
import no.kartverket.maskinportenmanagement.restserver.config.file
import no.kartverket.maskinportenmanagement.restserver.config.loadApplicationConfig
import no.kartverket.maskinportenmanagement.restserver.config.maskinportenManagementClient
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.maskinportenmanagement.restserver.observability.installCallLogging
import no.kartverket.maskinportenmanagement.restserver.problem.errorResponses
import no.kartverket.maskinportenmanagement.restserver.scopeaccess.scopeAccess
import no.kartverket.maskinportenmanagement.restserver.scopes.scopes

fun main() {
    val applicationConfig = loadApplicationConfig()
    // Logback reads this when the first logger is created, so it is set before anything that could log.
    applicationConfig.file("logback.configFile")?.let { System.setProperty("logback.configurationFile", it) }
    val config = AppConfig.load(applicationConfig)
    val digdirConfig = DigdirConfig.load(applicationConfig)

    embeddedServer(Netty, port = config.port) {
        module(
            client = maskinportenManagementClient(digdirConfig),
            externalFiltering = externalFiltering(config),
            accessLog = config.accessLog,
            accessLogHealth = config.accessLogHealth,
        )
    }.start(wait = true)
}

@OptIn(ExperimentalKtorApi::class)
fun Application.module(
    client: MaskinportenManagementClient,
    externalFiltering: ExternalFiltering,
    accessLog: Boolean = true,
    accessLogHealth: Boolean = false,
) {
    installCallLogging(accessLog, accessLogHealth)
    install(ContentNegotiation) { json(Json(DefaultJson) { explicitNulls = false }) }
    install(StatusPages) { errorResponses() }

    val spec by lazy { openApiSpec() }

    routing {
        get("/health/live") {
            call.respond(HttpStatusCode.OK)
        }.hide()

        get("/openapi") {
            call.respondText(spec, ContentType.Application.Json)
        }.hide()

        scopes(client, externalFiltering)
        scopeAccess(client, externalFiltering)
    }
}
