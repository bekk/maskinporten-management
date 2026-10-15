package com.maskinporten.routes.apis

import com.maskinporten.controllers.apis.ApisController
import io.ktor.server.routing.*

fun Route.apisRoutes() {

    val apisController = ApisController()

    route("/apis") {
        get {
            println("[route] GET /apis")
            apisController.getApis(call)
        }
    }
}

