package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.Serializable
import java.time.Instant

/** A consumer organization's access to a scope. */
public data class ScopeAccess(
    val scope: String,
    val ownerOrgno: String,
    val ownerOrganizationName: String?,
    val consumerOrgno: String,
    val consumerOrganizationName: String?,
    val state: ScopeAccessState,
    val created: Instant,
    val lastUpdated: Instant,
)

@Serializable
public enum class ScopeAccessState {
    REQUESTED,
    APPROVED,
    DENIED,
}
