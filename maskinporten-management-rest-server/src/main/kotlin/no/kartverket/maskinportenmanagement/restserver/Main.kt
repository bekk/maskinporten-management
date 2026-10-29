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
            val input = RequestInput()
            val certificate by input.clientCertificate(call, required = externalFiltering.isOn)
            input.requireValid()
            val filter = externalFiltering.filterFor(certificate)
            call.respond(client.listScopes().allowedBy(filter) { it.name }.map(ScopeDetails::from))
        }.describe(scopesListOperation)

        post("/api/scopes") {
            val input = RequestInput()
            input.noQuery(call)
            val scope by input.scopeJson(call)
            input.requireValid()
            call.respond(ScopeDetails.from(client.createScope(scope.value)))
        }.describe(scopeCreateOperation)

        put("/api/scopes") {
            val input = RequestInput()
            val scope by input.scopeQueryParameter(call)
            val body = call.jsonBody()
            input.requireValid()
            call.respond(ScopeDetails.from(client.updateScope(scope, body)))
        }.describe(scopeUpdateOperation)

        delete("/api/scopes") {
            val input = RequestInput()
            val scope by input.scopeQueryParameter(call)
            input.requireValid()
            call.respond(ScopeDetails.from(client.deleteScope(scope)))
        }.describe(scopeDeleteOperation)

        get("/api/scopeaccess/orgs") {
            val input = RequestInput()
            val scope by input.scopeQueryParameter(call)
            input.requireValid()
            call.respond(client.listScopeAccess(scope).map(ScopeAccess::from))
        }.describe(scopeAccessOrgsOperation)

        get("/api/scopeaccess/scopes") {
            val input = RequestInput()
            val orgnr by input.organizationNumberQueryParameter(call, "orgnr")
            val certificate by input.clientCertificate(call, required = externalFiltering.isOn)
            input.requireValid()
            val filter = externalFiltering.filterFor(certificate)
            call.respond(client.listConsumerScopeAccess(orgnr).allowedBy(filter) { it.scope }.map(ScopeAccess::from))
        }.describe(scopeAccessScopesOperation)

        get("/api/scopeaccess/changelog") {
            val input = RequestInput()
            val query = input.queryParameters(call.request.queryString(), "scope", "page", "size")
            val scope by input.scope(Location.QUERY, "scope", query["scope"])
            val page by input.wholeNumber(Location.QUERY, "page", query["page"], min = 0)
            val size by input.wholeNumber(Location.QUERY, "size", query["size"], min = 1)
            input.requireValid()
            call.respond(ScopeAccessChangelog.from(client.getScopeAccessChangelog(scope, page, size)))
        }.describe(scopeAccessChangelogOperation)

        put("/api/scopeaccess/{consumerOrgno}") {
            val input = RequestInput()
            val consumerOrgno by input.organizationNumber(Location.PATH, "consumerOrgno", call.pathParameters["consumerOrgno"])
            val scope by input.scopeQueryParameter(call)
            input.requireValid()
            call.respond(ScopeAccess.from(client.grantScopeAccess(consumerOrgno, scope)))
        }.describe(scopeAccessGrantOperation)

        delete("/api/scopeaccess/{consumerOrgno}") {
            val input = RequestInput()
            val consumerOrgno by input.organizationNumber(Location.PATH, "consumerOrgno", call.pathParameters["consumerOrgno"])
            val scope by input.scopeQueryParameter(call)
            input.requireValid()
            call.respond(ScopeAccess.from(client.removeScopeAccess(consumerOrgno, scope)))
        }.describe(scopeAccessRemoveOperation)
    }
}
