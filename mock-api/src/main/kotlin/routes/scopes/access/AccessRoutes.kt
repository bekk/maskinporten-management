package com.maskinporten.routes.scopes.access

import com.maskinporten.controllers.scopes.access.AccessController
import io.ktor.server.routing.*

fun Route.scopeAccessRoutes() {

    val accessController = AccessController()

    route("/access") {
        get("/all") {
            println("[route] GET /scopes/access/all")
            accessController.getAllScopeAccess(call)
        }
        get {
            println("[route] GET /scope/access")
            accessController.getScopeAccess(call)
        }

        get("/changelog") {
            println("[route] GET /scopes/access/changelog")
            accessController.getScopeAccessChangelog(call)
        }

        put("/{consumer_orgno}") {
            println("[route] PUT /scope/access")
            accessController.updateScopeAccess(call)
        }
        delete("/{consumer_orgno}") {
            println("[route] DELETE /scope/access")
            accessController.deleteScopeAccess(call)
        }
    }
}

