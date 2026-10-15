package com.maskinporten.controllers.apis

import com.maskinporten.services.apis.ApisService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

class ApisController {

    private val apisService = ApisService()

    suspend fun getApis(call: ApplicationCall) {
        println("[controller] getApis()")
        val apis = apisService.getApis()
        call.respond(apis)
    }
}