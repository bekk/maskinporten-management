package com.maskinporten.models

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class ErrorResponse(
    val status: Int,
    val timestamp: Instant = Clock.System.now(),
    val correlation_id: String,
    val errors: List<ApiError>?,
    val error_description: String,
    val error: String,
)

@Serializable
data class ApiError(
    val errorMessage: String,
    val isFieldError: Boolean,
    val objectName: String? = null,
    val fieldIdentifier: String? = null,
)