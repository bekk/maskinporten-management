package com.maskinporten.controllers.integrations

import com.maskinporten.services.integrations.IntegrationsService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlin.time.Instant

class IntegrationsController {

    private val integrationsService = IntegrationsService()

    suspend fun getIntegrations(call: ApplicationCall) {
        println("[controller] getIntegrations()")

        val orgNumber = call.request.queryParameters["org_number"]
        val updatedSince = call.request.queryParameters["updated_since"]

        val updatedSinceDate = updatedSince?.let { Instant.parse(it) }

        val integrations = integrationsService.getIntegrations(orgNumber, updatedSinceDate)

        call.respond(integrations)
    }
}
