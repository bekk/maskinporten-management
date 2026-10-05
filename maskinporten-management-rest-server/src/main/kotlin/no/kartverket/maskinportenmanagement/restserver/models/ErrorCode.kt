package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    MALFORMED_BODY,
    INTERNAL_ERROR,
}
