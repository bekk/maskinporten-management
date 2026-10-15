package com.maskinporten.controllers.scopes.access

import com.maskinporten.maskinporten.organization
import com.maskinporten.services.scopes.access.AccessService
import com.maskinporten.utils.requirePathParam
import com.maskinporten.utils.pageRequest
import com.maskinporten.utils.requireQueryParam
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class AccessController {

    private val accessService = AccessService()

    suspend fun getScopeAccess(call: ApplicationCall) {
        val consumerOrgNo = call.request.queryParameters["consumer_orgno"]
        val scope = call.request.queryParameters["scope"]
        val includeInactive = call.request.queryParameters["inactive"] == "true"

        println("[controller] getScopes()")
        val scopeAccess = accessService.getScopeAccess(consumerOrgNo, scope, includeInactive, call.organization)
        call.respond(scopeAccess)
    }

    suspend fun getScopeAccessChangelog(call: ApplicationCall) {
        val scopeName = requireQueryParam(call, "scopeName")

        val (page, size, sort) = pageRequest(call)

        println("[controller] getScopeAccessChangelog()")
        val changelog = accessService.getScopeAccessChangelog(scopeName, page, size, sort, call.organization)
        call.respond(changelog)
    }

    suspend fun updateScopeAccess(call: ApplicationCall) {
        val consumerOrgNo = requirePathParam(call, "consumer_orgno")
        val scope = requireQueryParam(call, "scope")

        println("[controller] updateScope()")
        val updatedScope = accessService.updateScopeAccess(consumerOrgNo, scope, call.organization)
        call.respond(updatedScope)
    }

    suspend fun deleteScopeAccess(call: ApplicationCall) {
        val consumerOrgNo = requirePathParam(call, "consumer_orgno")
        val scope = requireQueryParam(call, "scope")

        println("[controller] deleteScopeAccess()")
        val deletedAccess = accessService.deleteScopeAccess(consumerOrgNo, scope, call.organization)
        call.respond(deletedAccess)
    }

    suspend fun getAllScopeAccess(call: ApplicationCall) {
        val includeInactive = call.request.queryParameters["inactive"] == "true"

        println("[controller] getAllScopes()")
        val allScopes = accessService.getAllScopeAccess(includeInactive, call.organization)
        call.respond(allScopes)
    }
}
