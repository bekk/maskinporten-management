package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    INVALID_REQUEST,
    MALFORMED_BODY,
    UPSTREAM_ERROR,
    INTERNAL_ERROR,
}
