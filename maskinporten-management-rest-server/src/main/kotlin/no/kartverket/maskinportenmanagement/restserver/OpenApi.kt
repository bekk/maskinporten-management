package no.kartverket.maskinportenmanagement.restserver

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.server.application.Application
import io.ktor.server.application.plugin
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.openapi.plus
import kotlinx.serialization.json.Json

private val openApiJson = Json { prettyPrint = true }

private val apiInfo = OpenApiInfo(
    title = "Maskinporten Management REST API",
    version = "0.1.0",
    description = "REST API for managing Maskinporten scopes",
)

fun Application.openApiSpec(): String =
    openApiJson.encodeToString(OpenApiDoc(info = apiInfo) + plugin(RoutingRoot).getAllRoutes())

object OpenApiSpecFile {

    const val NAME: String = "openapi.json"

    fun contentsFor(servedSpec: String): String = servedSpec + "\n"
}

internal val healthLiveOperation: Operation.Builder.() -> Unit = {
    summary = "Liveness probe"
    description = "Returns 200 OK if the server is up. Not part of the stable API."
    responses {
        HttpStatusCode.OK {
            description = "OK"
        }
    }
}
