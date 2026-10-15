package com.maskinporten.models.delegationsources

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Delegationsource(
    val issuer: String,
    val name: String,
    val created: Instant,
    val lastUpdated: Instant,
)

