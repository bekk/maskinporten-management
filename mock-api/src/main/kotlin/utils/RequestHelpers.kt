package com.maskinporten.utils

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException

fun requireQueryParam(call: ApplicationCall, name: String): String {
    return call.request.queryParameters[name]
        ?.takeIf { it.isNotBlank() }
        ?: throw BadRequestException("Missing query parameter: $name")
}

fun requirePathParam(call: ApplicationCall, name: String): String {
    return call.parameters[name]
        ?.takeIf { it.isNotBlank() }
        ?: throw BadRequestException("Missing path parameter: $name")
}

data class PageRequest(val page: Int, val size: Int, val sort: List<String>)

// Spring-style paging params: page (default 0), size (default 20), sort as "property,(asc|desc)" (repeatable)
fun pageRequest(call: ApplicationCall): PageRequest {
    val params = call.request.queryParameters
    val page = params["page"]?.let { it.toIntOrNull() ?: throw BadRequestException("Invalid page: $it") } ?: 0
    val size = params["size"]?.let { it.toIntOrNull() ?: throw BadRequestException("Invalid size: $it") } ?: 20
    if (page < 0) throw BadRequestException("page must be 0 or greater")
    if (size < 1) throw BadRequestException("size must be 1 or greater")
    return PageRequest(page, size, params.getAll("sort").orEmpty())
}
