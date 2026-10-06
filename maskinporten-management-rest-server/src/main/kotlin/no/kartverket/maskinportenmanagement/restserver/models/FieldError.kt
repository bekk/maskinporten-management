package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.Serializable
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode

@Serializable
data class FieldError(val field: String, val code: ValidationCode, val message: String)
