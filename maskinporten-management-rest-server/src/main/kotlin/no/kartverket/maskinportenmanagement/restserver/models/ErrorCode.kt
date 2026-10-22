package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    VALIDATION_ERROR,
    MALFORMED_REQUEST,
    UPSTREAM_REJECTED,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UPSTREAM_ERROR,
    INTERNAL_ERROR,
}
