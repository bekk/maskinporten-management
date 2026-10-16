package com.maskinporten.models.changelog

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class ChangelogContent(
    val id: Int,
    val created: Instant,
    val message: String,
    val actor_name: String,
    val channel: String,
    val diff: String,
)
