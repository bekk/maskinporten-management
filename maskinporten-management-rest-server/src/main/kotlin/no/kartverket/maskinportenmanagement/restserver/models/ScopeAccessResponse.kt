package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.Serializable

@Serializable
data class ScopeAccessResponse(
    val access: List<ConsumerAccess>,
)
