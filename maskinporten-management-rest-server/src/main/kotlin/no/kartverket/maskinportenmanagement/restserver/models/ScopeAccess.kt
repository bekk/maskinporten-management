package no.kartverket.maskinportenmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.ScopeAccessState
import kotlin.time.Instant
import no.kartverket.maskinportenmanagement.client.ScopeAccess as ClientScopeAccess

@Serializable
data class ScopeAccess(
    @JsonSchema.Description("The scope.")
    val scope: Scope,

    @JsonSchema.Description("The organisation that owns the scope: Kartverket.")
    @SerialName("owner_orgno")
    val ownerOrgno: OrganizationNumber,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "The name of the organisation that owns the scope. Left out when Digdir has no name for it.",
    )
    @SerialName("owner_organization_name")
    val ownerOrganizationName: String? = null,

    @JsonSchema.Description("The organisation that was given access.")
    @SerialName("consumer_orgno")
    val consumerOrgno: OrganizationNumber,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "The name of the organisation that was given access. Left out when Digdir has no name for it.",
    )
    @SerialName("consumer_organization_name")
    val consumerOrganizationName: String? = null,

    @JsonSchema.Description(
        "Where the access stands. Only `APPROVED` means the organisation has access, and giving access through this " +
            "API sets it at once. Digdir does not document when it uses `REQUESTED` and `DENIED`. `UNKNOWN` is a " +
            "state Digdir has added that this API does not know yet.",
    )
    val state: ScopeAccessState,

    @JsonSchema.Format("date-time")
    @JsonSchema.Description("When the access was given.")
    val created: Instant,

    @JsonSchema.Format("date-time")
    @JsonSchema.Description("When the access was last changed.")
    @SerialName("last_updated")
    val lastUpdated: Instant,
) {
    companion object {
        fun from(access: ClientScopeAccess) = ScopeAccess(
            scope = access.scope,
            ownerOrgno = access.ownerOrgno,
            ownerOrganizationName = access.ownerOrganizationName,
            consumerOrgno = access.consumerOrgno,
            consumerOrganizationName = access.consumerOrganizationName,
            state = access.state,
            created = access.created,
            lastUpdated = access.lastUpdated,
        )
    }
}
