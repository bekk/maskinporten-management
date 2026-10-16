package com.maskinporten.controllers.delegationsources

import com.maskinporten.services.delegationsources.DelegationsourcesService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class DelegationsourcesController {

    private val delegationsourcesService = DelegationsourcesService()

    suspend fun getDelegationsources(call: ApplicationCall) {
        println("[controller] getDelegationsources()")
        val delegationsources = delegationsourcesService.getDelegationsources()
        call.respond(delegationsources)
    }
}
