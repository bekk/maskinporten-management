package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScopeAccess(
    val scope: String,
    @SerialName("owner_orgno") val ownerOrgno: String,
    @SerialName("owner_organization_name") val ownerOrganizationName: String,
    @SerialName("consumer_orgno") val consumerOrgno: String,
    @SerialName("consumer_organization_name") val consumerOrganizationName: String,
    val state: ScopeAccessState,
    val created: String,
    @SerialName("last_updated") val lastUpdated: String,
)

@Serializable
enum class ScopeAccessState {
    REQUESTED,
    APPROVED,
    DENIED,
}
