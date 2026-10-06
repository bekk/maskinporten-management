package no.kartverket.maskinportenmanagement.client.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.maskinportenmanagement.client.ScopeAccess
import no.kartverket.maskinportenmanagement.client.ScopeAccessState
import java.time.Instant

@Serializable
internal data class AccessResponse(
    val scope: String,
    @SerialName("owner_orgno") val ownerOrgno: String,
    @SerialName("owner_organization_name") val ownerOrganizationName: String? = null,
    @SerialName("consumer_orgno") val consumerOrgno: String,
    @SerialName("consumer_organization_name") val consumerOrganizationName: String? = null,
    val state: ScopeAccessState,
    @Serializable(with = InstantSerializer::class) val created: Instant,
    @Serializable(with = InstantSerializer::class)
    @SerialName("last_updated")
    val lastUpdated: Instant,
) {
    fun toScopeAccess(): ScopeAccess = ScopeAccess(
        scope = scope,
        ownerOrgno = ownerOrgno,
        ownerOrganizationName = ownerOrganizationName,
        consumerOrgno = consumerOrgno,
        consumerOrganizationName = consumerOrganizationName,
        state = state,
        created = created,
        lastUpdated = lastUpdated,
    )
}
