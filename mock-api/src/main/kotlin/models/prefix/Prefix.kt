package com.maskinporten.models.prefix

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Prefix(
    val prefix: String,
    val owner_orgno: String,
    val organization_name: String? = null,
    val created: Instant,
    val last_updated: Instant,
    val active: Boolean,
)
