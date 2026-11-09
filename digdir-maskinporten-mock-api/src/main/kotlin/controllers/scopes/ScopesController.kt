package com.maskinporten.controllers.scopes

import com.maskinporten.dto.CreateScopeRequest
import com.maskinporten.dto.UpdateScopeRequest
import com.maskinporten.maskinporten.organization
import com.maskinporten.models.scopes.IntegrationType
import com.maskinporten.services.scopes.ScopesService
import com.maskinporten.utils.requireQueryParam
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond

class ScopesController {

    private val scopesService = ScopesService()

    suspend fun getScopes(call: ApplicationCall) {
        println("[controller] getScopes()")
        val inactive = call.request.queryParameters["inactive"] == "true"

        val scopes = scopesService.getScopes(inactive, call.organization)
        call.respond(scopes)
    }

    suspend fun createScope(call: ApplicationCall) {
        println("[controller] createScope()")
        val request = call.receive<CreateScopeRequest>()

        val createdScope = scopesService.createScope(request, call.organization)
        call.respond(createdScope)
    }

    suspend fun updateScope(call: ApplicationCall) {
        println("[controller] updateScope()")
        val scope = requireQueryParam(call, "scope")
        val request = call.receive<UpdateScopeRequest>()

        val updatedScope = scopesService.updateScope(scope, request, call.organization)
        call.respond(updatedScope)
    }

    suspend fun deleteScope(call: ApplicationCall) {
        println("[controller] deleteScope()")
        val scope = requireQueryParam(call, "scope")

        val deletedScope = scopesService.deleteScope(scope, call.organization)
        call.respond(deletedScope)
    }

    suspend fun getScopeByName(call: ApplicationCall) {
        println("[controller] getScopeByName()")
        val scope = requireQueryParam(call, "scope")

        val foundScope = scopesService.getScopeByName(scope, call.organization)
        call.respond(foundScope)
    }

    suspend fun getAllScopes(call: ApplicationCall) {
        println("[controller] getAllScopes()")

        val prefix = call.request.queryParameters["prefix"]
        val includeInactive = call.request.queryParameters["inactive"] == "true"
        val onlyWithDelegationSource = call.request.queryParameters["delegated_sources"] == "true"
        val onlyAccessibleForAll = call.request.queryParameters["accessible_for_all"] == "true"
        val integrationTypeString = call.request.queryParameters["integration_type"]
        val integrationType = integrationTypeString?.let { parseIntegrationTypeQueryParam(it) }

        val allScopes = scopesService.getAllScopes(
            prefix,
            includeInactive,
            onlyWithDelegationSource,
            onlyAccessibleForAll,
            integrationType)
        call.respond(allScopes)
    }

    // The integration_type query param uses different names than the lowercase body values (see maskinporten-api.json)
    private fun parseIntegrationTypeQueryParam(value: String): IntegrationType = when (value) {
        "ANSATTPORTEN" -> IntegrationType.ANSATTPORTEN
        "API_CLIENT" -> IntegrationType.API_KLIENT
        "E_FORMIDLING" -> IntegrationType.EFORMIDLING
        "ID_PORTEN" -> IntegrationType.IDPORTEN
        "ID_PORTEN_SAML2" -> IntegrationType.IDPORTEN_SAML2
        "KRR" -> IntegrationType.KRR
        "MASKINPORTEN" -> IntegrationType.MASKINPORTEN
        else -> throw BadRequestException("Invalid integration_type: $value")
    }
}
