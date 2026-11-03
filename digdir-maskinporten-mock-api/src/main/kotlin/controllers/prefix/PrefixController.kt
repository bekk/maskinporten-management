package com.maskinporten.controllers.prefix

import com.maskinporten.maskinporten.organization
import com.maskinporten.services.prefix.PrefixService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class PrefixController {
    private val prefixService = PrefixService()

    suspend fun getPrefix(call: ApplicationCall) {
        println("[controller] getPrefix()")

        val prefix = prefixService.getPrefix(call.organization)
        call.respond(prefix)
    }

    suspend fun getAllPrefix(call: ApplicationCall) {
        println("[controller] getAllPrefix()")

        val includeInactive = call.request.queryParameters["inactive"] == "true"
        val prefix = prefixService.getAllPrefix(includeInactive)
        call.respond(prefix)
    }
}
