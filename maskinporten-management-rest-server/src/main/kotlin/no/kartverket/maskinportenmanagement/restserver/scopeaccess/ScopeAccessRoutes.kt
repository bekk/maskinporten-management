package no.kartverket.maskinportenmanagement.restserver.scopeaccess

import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.put
import io.ktor.utils.io.ExperimentalKtorApi
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.restserver.Location
import no.kartverket.maskinportenmanagement.restserver.RequestInput
import no.kartverket.maskinportenmanagement.restserver.clientCertificate
import no.kartverket.maskinportenmanagement.restserver.config.externalFiltering
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFiltering
import no.kartverket.maskinportenmanagement.restserver.filtering.allowedBy
import no.kartverket.maskinportenmanagement.restserver.organizationNumber
import no.kartverket.maskinportenmanagement.restserver.organizationNumberQueryParameter
import no.kartverket.maskinportenmanagement.restserver.queryParameters
import no.kartverket.maskinportenmanagement.restserver.scope
import no.kartverket.maskinportenmanagement.restserver.scopeQueryParameter
import no.kartverket.maskinportenmanagement.restserver.wholeNumber

@OptIn(ExperimentalKtorApi::class)
internal fun Route.scopeAccess(client: MaskinportenManagementClient, externalFiltering: ExternalFiltering) {
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
