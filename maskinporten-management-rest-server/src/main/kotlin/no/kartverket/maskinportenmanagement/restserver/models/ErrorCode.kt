package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    INVALID_REQUEST,
    UPSTREAM_ERROR,
    INTERNAL_ERROR,
}
