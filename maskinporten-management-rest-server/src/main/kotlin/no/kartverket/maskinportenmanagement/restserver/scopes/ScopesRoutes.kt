package no.kartverket.maskinportenmanagement.restserver.scopes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.ExperimentalKtorApi
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.restserver.RequestInput
import no.kartverket.maskinportenmanagement.restserver.clientCertificate
import no.kartverket.maskinportenmanagement.restserver.config.externalFiltering
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.maskinportenmanagement.restserver.filtering.allowedBy
import no.kartverket.maskinportenmanagement.restserver.jsonBody
import no.kartverket.maskinportenmanagement.restserver.noQuery
import no.kartverket.maskinportenmanagement.restserver.scopeJson
import no.kartverket.maskinportenmanagement.restserver.scopeQueryParameter

@OptIn(ExperimentalKtorApi::class)
internal fun Route.scopes(client: MaskinportenManagementClient, externalFiltering: ExternalFiltering) {
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
}
