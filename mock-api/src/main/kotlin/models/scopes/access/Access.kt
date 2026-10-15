package com.maskinporten.models.scopes.access

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Access(
    val scope: String,
    val owner_orgno: String,
    val owner_organization_name: String?,
    val consumer_orgno: String,
    val consumer_organization_name: String?,
    val state: State,
    val created: Instant,
    val last_updated: Instant,
    )

enum class State {
    REQUESTED,
    APPROVED,
    DENIED
}