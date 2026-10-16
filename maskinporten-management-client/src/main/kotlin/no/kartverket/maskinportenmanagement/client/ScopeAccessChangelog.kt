package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
public data class ScopeAccessChangelog(
    public val content: List<ScopeAccessChange>,
    public val page: PageMetadata,
)

// Digdir does not document which fields it always sends. All but the id and time are only for people to read, so an
// entry without them is still a change
@Serializable
public data class ScopeAccessChange(
    public val id: Long,
    public val created: Instant,
    public val message: String? = null,
    @SerialName("actor_name") public val actorName: String? = null,
    public val channel: String? = null,
    public val diff: String? = null,
)

@Serializable
public data class PageMetadata(
    public val size: Long,
    public val number: Long,
    public val totalElements: Long,
    public val totalPages: Long,
)
