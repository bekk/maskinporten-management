package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.Serializable
import no.kartverket.maskinportenmanagement.client.ScopeAccess
import no.kartverket.maskinportenmanagement.client.ScopeAccessState

@Serializable
data class ConsumerAccess(
    val scope: String,
    val ownerOrgno: String,
    val ownerOrganizationName: String?,
    val consumerOrgno: String,
    val consumerOrganizationName: String?,
    val state: ScopeAccessState,
    val created: String,
    val lastUpdated: String,
) {
    companion object {
        fun of(access: ScopeAccess): ConsumerAccess = ConsumerAccess(
            scope = access.scope,
            ownerOrgno = access.ownerOrgno,
            ownerOrganizationName = access.ownerOrganizationName,
            consumerOrgno = access.consumerOrgno,
            consumerOrganizationName = access.consumerOrganizationName,
            state = access.state,
            created = access.created.toString(),
            lastUpdated = access.lastUpdated.toString(),
        )
    }
}
