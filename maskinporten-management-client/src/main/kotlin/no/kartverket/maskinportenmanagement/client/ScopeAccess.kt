package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// The organisation names are only for people to read, so an answer without them is still an access
@Serializable
public data class ScopeAccess(
    public val scope: Scope,
    @SerialName("owner_orgno") public val ownerOrgno: OrganizationNumber,
    @SerialName("owner_organization_name") public val ownerOrganizationName: String? = null,
    @SerialName("consumer_orgno") public val consumerOrgno: OrganizationNumber,
    @SerialName("consumer_organization_name") public val consumerOrganizationName: String? = null,
    @Serializable(with = ScopeAccessStateSerializer::class) public val state: ScopeAccessState,
    public val created: Instant,
    @SerialName("last_updated") public val lastUpdated: Instant,
)

@Serializable
public enum class ScopeAccessState {
    REQUESTED,
    APPROVED,
    DENIED,
    UNKNOWN,
}

internal object ScopeAccessStateSerializer :
    OrUnknownSerializer<ScopeAccessState>(ScopeAccessState.serializer(), ScopeAccessState.entries, ScopeAccessState.UNKNOWN)
