package no.kartverket.__pkg__.restserver.models

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    MALFORMED_REQUEST,
    VALIDATION_ERROR,
    UPSTREAM_REJECTED,
    UPSTREAM_ERROR,
    INTERNAL_ERROR,
}
