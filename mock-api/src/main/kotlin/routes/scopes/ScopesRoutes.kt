package com.maskinporten.routes.scopes

import com.maskinporten.controllers.scopes.ScopesController
import com.maskinporten.routes.scopes.access.scopeAccessRoutes
import io.ktor.server.routing.*

fun Route.scopesRoutes() {

    val scopesController = ScopesController()

    route("/scopes") {
        scopeAccessRoutes()

        get {
            println("[route] GET /scopes")
            scopesController.getScopes(call)
        }
        post {
            println("[route] POST /scopes")
            scopesController.createScope(call)
        }
        put {
            println("[route] PUT /scopes")
            scopesController.updateScope(call)
        }
        delete {
            println("[route] DELETE /scopes")
            scopesController.deleteScope(call)
        }
        get("/scope") {
            println("[route] GET /scopes/scope")
            scopesController.getScopeByName(call)
        }
        get("/all") {
            println("[route] GET /scopes/all")
            scopesController.getAllScopes(call)
        }
    }
}

