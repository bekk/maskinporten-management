package com.maskinporten.routes.delegationsources

import com.maskinporten.controllers.delegationsources.DelegationsourcesController
import io.ktor.server.routing.*

fun Route.delegationsourcesRoutes() {

    val delegationsourcesController = DelegationsourcesController()

    route("/delegationsources") {
        get {
            println("[route] GET /delegationsources")
            delegationsourcesController.getDelegationsources(call)
        }
    }
}

