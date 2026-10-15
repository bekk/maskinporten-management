package com.maskinporten.routes.integrations

import com.maskinporten.controllers.integrations.IntegrationsController
import io.ktor.server.routing.*

fun Route.integrationsRoutes() {

    val integrationsController = IntegrationsController()

    route("/integrations") {
        get {
            println("[route] GET /integrations")
            integrationsController.getIntegrations(call)
        }
    }
}

