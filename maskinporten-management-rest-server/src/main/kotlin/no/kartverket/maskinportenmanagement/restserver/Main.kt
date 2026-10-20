package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.ScopeUpdateJson
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccess
import no.kartverket.maskinportenmanagement.restserver.models.ScopeAccessChangelog
import no.kartverket.maskinportenmanagement.restserver.models.ScopeDetails
import org.slf4j.LoggerFactory
import java.util.UUID

fun main() {
    val config = AppConfig.load()
    config.logbackConfigFile?.let { System.setProperty("logback.configurationFile", it) }

    embeddedServer(Netty, port = config.port) {
        module(
            client = maskinportenManagementClient(config),
            externalFiltering = externalFiltering(config),
            accessLog = config.accessLog,
        )
    }.start(wait = true)
}

@OptIn(ExperimentalKtorApi::class)
fun Application.module(
    client: MaskinportenManagementClient,
    externalFiltering: ExternalFiltering,
    accessLog: Boolean = true,
) {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify(::isSafeRequestId)
    }
    install(CallLogging) {
        mdc("workload") { workloadFrom(it.request.headers[CLIENT_CERTIFICATE_HEADER]) }
        callIdMdc("request_id")

        logger = LoggerFactory.getLogger("access")
        disableDefaultColors()
        filter { accessLog }
    }
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

        get("/api/scopes") {
            val filter = externalFiltering.filterFor(call)
            call.respond(client.listScopes(filter).map(ScopeDetails::from))
        }.describe(scopesListOperation)

        post("/api/scopes") {
            call.noQuery()
            val scope = call.scopeJsonBody()
            call.respond(ScopeDetails.from(client.createScope(scope)))
        }.describe(scopeCreateOperation)

        put("/api/scopes") {
            val scope = call.scopeQueryParameter("scope")
            call.respond(ScopeDetails.from(client.updateScope(scope, ScopeUpdateJson.of(call.jsonBody()))))
        }.describe(scopeUpdateOperation)

        delete("/api/scopes") {
            val scope = call.scopeQueryParameter("scope")
            call.respond(ScopeDetails.from(client.deleteScope(scope)))
        }.describe(scopeDeleteOperation)

        get("/api/scopeaccess/orgs") {
            val scope = call.scopeQueryParameter("scope")
            call.respond(client.listScopeAccess(scope).map(ScopeAccess::from))
        }.describe(scopeAccessOrgsOperation)

        get("/api/scopeaccess/scopes") {
            val orgnr = call.organizationNumberQueryParameter("orgnr")
            val filter = externalFiltering.filterFor(call)
            call.respond(client.listConsumerScopeAccess(orgnr, filter).map(ScopeAccess::from))
        }.describe(scopeAccessScopesOperation)

        get("/api/scopeaccess/changelog") {
            val query = queryParameters(call.request.queryString(), "scope", "page", "size")
            val scope = scopeParameter("scope", query.getValue("scope"))
            val page = query["page"]?.let { wholeNumberParameter("page", it, min = 0) }
            val size = query["size"]?.let { wholeNumberParameter("size", it, min = 1) }
            call.respond(ScopeAccessChangelog.from(client.getScopeAccessChangelog(scope, page, size)))
        }.describe(scopeAccessChangelogOperation)

        put("/api/scopeaccess/{consumerOrgno}") {
            val consumerOrgno = call.organizationNumberPathParameter("consumerOrgno")
            val scope = call.scopeQueryParameter("scope")
            call.respond(ScopeAccess.from(client.grantScopeAccess(consumerOrgno, scope)))
        }.describe(scopeAccessGrantOperation)

        delete("/api/scopeaccess/{consumerOrgno}") {
            val consumerOrgno = call.organizationNumberPathParameter("consumerOrgno")
            val scope = call.scopeQueryParameter("scope")
            call.respond(ScopeAccess.from(client.removeScopeAccess(consumerOrgno, scope)))
        }.describe(scopeAccessRemoveOperation)
    }
}
