package no.kartverket.maskinportenmanagement.restserver

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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient

fun main() {
    val config = loadApplicationConfig()
    logbackConfigFile(config)?.let { System.setProperty("logback.configurationFile", it) }
    embeddedServer(
        Netty,
        environment = applicationEnvironment { this.config = config },
        configure = { connector { port = config.port("server.port") } },
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    configureAccessLogging()
    configureSerialization()
    configureErrorHandling()
    configureMaskinportenManagement()
    configureRouting()
}

@OptIn(ExperimentalKtorApi::class)
fun Application.configureRouting() {
    val spec: String by lazy { openApiSpec() }

    routing {
        get("/health/live") {
            call.respond(HttpStatusCode.OK)
        }.describe(healthLiveOperation)

        get("/openapi") {
            call.respondText(spec, ContentType.Application.Json)
        }.hide()

        get("/api/scopes") {
            val client: MaskinportenManagementClient by dependencies
            val externalFiltering: ExternalFiltering by dependencies
            val filter = externalFiltering.filterFor(call)
            call.respondFromDigdir(client.listScopes(filter))
        }.describe(scopesListOperation)

        get("/api/scopeaccess/orgs") {
            val client: MaskinportenManagementClient by dependencies
            val scope = call.onlyQueryParameter("scope")
            call.respondFromDigdir(client.listScopeAccess(scope))
        }.describe(scopeAccessOrgsOperation)

        get("/api/scopeaccess/scopes") {
            val client: MaskinportenManagementClient by dependencies
            val externalFiltering: ExternalFiltering by dependencies
            val orgnr = call.organizationNumberQueryParameter("orgnr")
            val filter = externalFiltering.filterFor(call)
            call.respondFromDigdir(client.listConsumerScopeAccess(orgnr, filter))
        }.describe(scopeAccessScopesOperation)

        put("/api/scopeaccess/{consumerOrgno}") {
            val client: MaskinportenManagementClient by dependencies
            val consumerOrgno = call.organizationNumberPathParameter("consumerOrgno")
            val scope = call.onlyQueryParameter("scope")
            call.respondFromDigdir(client.grantScopeAccess(consumerOrgno, scope))
        }.describe(scopeAccessGrantOperation)

        delete("/api/scopeaccess/{consumerOrgno}") {
            val client: MaskinportenManagementClient by dependencies
            val consumerOrgno = call.organizationNumberPathParameter("consumerOrgno")
            val scope = call.onlyQueryParameter("scope")
            call.respondFromDigdir(client.removeScopeAccess(consumerOrgno, scope))
        }.describe(scopeAccessRemoveOperation)
    }
}
