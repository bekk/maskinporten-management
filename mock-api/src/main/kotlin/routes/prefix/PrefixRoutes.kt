package com.maskinporten.routes.prefix

import com.maskinporten.controllers.prefix.PrefixController
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.prefixRoutes() {
    val prefixController = PrefixController()

    route("/prefix") {
        get {
            println("[route] GET /prefix")
            prefixController.getPrefix(call)
        }
        get("/all") {
            println("[route] GET /prefix/all")
            prefixController.getAllPrefix(call)
        }
    }
}
