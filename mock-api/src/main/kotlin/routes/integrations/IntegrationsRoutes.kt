package com.maskinporten.routes.integrations

import com.maskinporten.controllers.integrations.IntegrationsController
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.integrationsRoutes() {
    val integrationsController = IntegrationsController()

    route("/integrations") {
        get {
            println("[route] GET /integrations")
            integrationsController.getIntegrations(call)
        }
    }
}
