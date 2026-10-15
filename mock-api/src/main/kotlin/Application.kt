package com.maskinporten

import com.maskinporten.database.DatabaseFactory
import com.maskinporten.errors.ConflictException
import com.maskinporten.filtering.filteringRoutes
import com.maskinporten.maskinporten.ForbiddenException
import com.maskinporten.maskinporten.UnauthorizedException
import com.maskinporten.maskinporten.maskinportenMockFromConfig
import com.maskinporten.maskinporten.maskinportenRoutes
import com.maskinporten.maskinporten.requireMaskinportenToken
import com.maskinporten.models.ApiError
import com.maskinporten.routes.apis.apisRoutes
import com.maskinporten.routes.integrations.integrationsRoutes
import com.maskinporten.routes.scopes.scopesRoutes
import com.maskinporten.routes.delegationsources.delegationsourcesRoutes
import com.maskinporten.routes.prefix.prefixRoutes
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import com.maskinporten.models.ErrorResponse
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import kotlinx.serialization.json.Json
import kotlin.time.Clock

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val dbHost = environment.config.propertyOrNull("ktor.database.host")?.getString()
        ?: System.getenv("DB_HOST") ?: "localhost"
    val dbPort = environment.config.propertyOrNull("ktor.database.port")?.getString()
        ?: System.getenv("DB_PORT") ?: "5433"
    val dbName = environment.config.propertyOrNull("ktor.database.name")?.getString()
        ?: System.getenv("DB_NAME") ?: "maskinporten-db"
    val dbUser = environment.config.propertyOrNull("ktor.database.user")?.getString()
        ?: System.getenv("DB_USER") ?: "admin"
    val dbPassword = environment.config.propertyOrNull("ktor.database.password")?.getString()
        ?: System.getenv("DB_PASSWORD") ?: "admin"

    val dataSource = DatabaseFactory.init(
        url = "jdbc:postgresql://$dbHost:$dbPort/$dbName",
        user = dbUser,
        password = dbPassword
    )
    monitor.subscribe(ApplicationStopped) { dataSource.close() }

    val maskinporten = maskinportenMockFromConfig(environment.config, log)

    install(ContentNegotiation) {
        json(Json{
            explicitNulls = false
        })
    }
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.application.log.warn(
                "BadRequestException for ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message ?: ""}"
            )
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    HttpStatusCode.BadRequest.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            cause.message ?: "Bad Request",
                            true,
                        )
                    ),
                    cause.message ?: "Bad Request",

                    cause.message ?: "Bad Request"
                )
            )
        }
        exception<NotFoundException> { call, cause ->
            call.application.log.warn(
                "NotFoundException for ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message ?: ""}"
            )
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    HttpStatusCode.NotFound.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            cause.message ?: "Not Found",
                            false,
                        )
                    ),
                    cause.message ?: "Not Found",
                    cause.message ?: "Not Found"
                )
            )
        }
        exception<ConflictException> { call, cause ->
            call.application.log.warn(
                "ConflictException for ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message ?: ""}"
            )
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    HttpStatusCode.Conflict.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            cause.message ?: "Conflict",
                            false,
                        )
                    ),
                    cause.message ?: "Conflict",
                    cause.message ?: "Conflict"
                )
            )
        }
        exception<UnauthorizedException> { call, cause ->
            call.application.log.warn(
                "UnauthorizedException for ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message ?: ""}"
            )
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(
                    HttpStatusCode.Unauthorized.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            cause.message ?: "Unauthorized",
                            false,
                        )
                    ),
                    cause.message ?: "Unauthorized",
                    cause.message ?: "Unauthorized"
                )
            )
        }
        exception<ForbiddenException> { call, cause ->
            call.application.log.warn(
                "ForbiddenException for ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message ?: ""}"
            )
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(
                    HttpStatusCode.Forbidden.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            cause.message ?: "Forbidden",
                            false,
                        )
                    ),
                    cause.message ?: "Forbidden",
                    cause.message ?: "Forbidden"
                )
            )
        }
        exception<IllegalArgumentException> { call, cause ->
            call.application.log.warn(
                "IllegalArgumentException for ${call.request.httpMethod.value} ${call.request.uri}: ${cause.message ?: ""}"
            )
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    HttpStatusCode.BadRequest.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            cause.message ?: "Bad Request",
                            true,
                        )
                    ),
                    cause.message ?: "Bad Request",

                    cause.message ?: "Bad Request"
                )
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error(
                "Unhandled exception for ${call.request.httpMethod.value} ${call.request.uri}",
                cause
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    HttpStatusCode.InternalServerError.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            cause.message ?: "Internal Server Error",
                            false,
                        )
                    ),
                    cause.message ?: "Internal Server Error",

                    cause.message ?: "Internal Server Error",
                )
            )
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    HttpStatusCode.NotFound.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            "Not Found",
                            false,
                        )
                    ),
                    "Not Found",
                    "Not Found"
                )
            )
        }
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                ErrorResponse(
                    HttpStatusCode.MethodNotAllowed.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            "Method Not allowed",
                            false,
                        )
                    ),
                    "Method Not allowed",

                    "Method Not allowed"
                )
            )
        }
        status(HttpStatusCode.UnsupportedMediaType) { call, status ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ErrorResponse(
                    HttpStatusCode.UnsupportedMediaType.value,
                    Clock.System.now(),
                    "example-correlation-id",
                    listOf(
                        ApiError(
                            "Unsupported Media Type",
                            false,
                        )
                    ),
                    "Unsupported Media Type",

                    "Unsupported Media Type"
                )
            )
        }
    }
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        swaggerUI(path = "swagger", swaggerFile = "openapi/maskinporten-api.json")
        maskinporten?.let { maskinportenRoutes(it) }
        filteringRoutes()
        route("/api/v1") {
            maskinporten?.let { install(requireMaskinportenToken(it)) }
            scopesRoutes()
            delegationsourcesRoutes()
            integrationsRoutes()
            prefixRoutes()
            apisRoutes()
        }
    }
}
