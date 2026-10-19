package no.kartverket.__pkg__.restserver.models

import kotlinx.serialization.Serializable
import no.kartverket.__pkg__.client.validation.ValidationCode

@Serializable
data class FieldError(val field: String, val code: ValidationCode, val message: String)
