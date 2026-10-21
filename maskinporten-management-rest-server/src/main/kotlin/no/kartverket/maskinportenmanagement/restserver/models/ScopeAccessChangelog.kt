package no.kartverket.maskinportenmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import no.kartverket.maskinportenmanagement.client.PageMetadata as ClientPageMetadata
import no.kartverket.maskinportenmanagement.client.ScopeAccessChange as ClientScopeAccessChange
import no.kartverket.maskinportenmanagement.client.ScopeAccessChangelog as ClientScopeAccessChangelog

@Serializable
data class ScopeAccessChangelog(
    @JsonSchema.Description("The changes on this page.")
    val content: List<ScopeAccessChange>,

    val page: PageMetadata,
) {
    companion object {
        fun from(changelog: ClientScopeAccessChangelog) = ScopeAccessChangelog(
            content = changelog.content.map(ScopeAccessChange::from),
            page = PageMetadata.from(changelog.page),
        )
    }
}

@Serializable
data class ScopeAccessChange(
    @JsonSchema.Description("Digdir's id for the change.")
    val id: Long,

    @JsonSchema.Format("date-time")
    @JsonSchema.Description("When the change was made.")
    val created: Instant,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("Digdir's short summary of the change. Left out when Digdir sends none.")
    val message: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "Who made the change, as Digdir records it. Changes made through this API are made with this API's " +
            "Maskinporten client, not as the calling app. Left out when Digdir sends none.",
    )
    @SerialName("actor_name")
    val actorName: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "Where the change was made, as Digdir records it. Digdir does not document the values. Left out when Digdir " +
            "sends none.",
    )
    val channel: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "What changed, as JSON in a string. Digdir does not document its format. Left out when Digdir sends none.",
    )
    val diff: String? = null,
) {
    companion object {
        fun from(change: ClientScopeAccessChange) = ScopeAccessChange(
            id = change.id,
            created = change.created,
            message = change.message,
            actorName = change.actorName,
            channel = change.channel,
            diff = change.diff,
        )
    }
}

@Serializable
@JsonSchema.Description("Where this page is in the whole changelog.")
data class PageMetadata(
    @JsonSchema.Description("The most changes a page holds.")
    val size: Long,

    @JsonSchema.Description("This page's number, counting from 0.")
    val number: Long,

    @JsonSchema.Description("How many changes there are in all.")
    val totalElements: Long,

    @JsonSchema.Description("How many pages there are in all.")
    val totalPages: Long,
) {
    companion object {
        fun from(page: ClientPageMetadata) = PageMetadata(
            size = page.size,
            number = page.number,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }
}
