package no.kartverket.maskinportenmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.kartverket.maskinportenmanagement.client.IntegrationType
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.TokenType
import no.kartverket.maskinportenmanagement.client.Visibility
import kotlin.time.Instant
import no.kartverket.maskinportenmanagement.client.ScopeDetails as ClientScopeDetails

@Serializable
data class ScopeDetails(
    @JsonSchema.Description("The whole scope: the prefix, a colon and the subscope.")
    val name: Scope,

    @JsonSchema.Description("Digdir's prefix for the owner, `kartverk` for Kartverket: only the part before the colon.")
    val prefix: String,

    @JsonSchema.Description("The part after the colon, which the owner chose. It cannot be changed.")
    val subscope: String,

    @JsonSchema.Description("A short description in Norwegian Bokmål.")
    val description: String,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("A longer description in Norwegian Bokmål. Left out, or empty, if the owner has not written one.")
    @SerialName("long_description")
    val longDescription: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("`description` in English. Left out if the owner has not written one.")
    @SerialName("description#en")
    val descriptionEn: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("`description` in Norwegian Nynorsk. Left out if the owner has not written one.")
    @SerialName("description#nn")
    val descriptionNn: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("`description` in Northern Sami. Left out if the owner has not written one.")
    @SerialName("description#se")
    val descriptionSe: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("`long_description` in English. Left out if the owner has not written one.")
    @SerialName("long_description#en")
    val longDescriptionEn: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("`long_description` in Norwegian Nynorsk. Left out if the owner has not written one.")
    @SerialName("long_description#nn")
    val longDescriptionNn: String? = null,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("`long_description` in Northern Sami. Left out if the owner has not written one.")
    @SerialName("long_description#se")
    val longDescriptionSe: String? = null,

    @JsonSchema.Description(
        "Always `true` in `GET /api/scopes`, which lists only active scopes, and `false` once the scope is deleted.",
    )
    val active: Boolean,

    @JsonSchema.Format("date-time")
    @JsonSchema.Description("When the scope was created.")
    val created: Instant,

    @JsonSchema.Format("date-time")
    @JsonSchema.Description("When the scope was last changed.")
    @SerialName("last_updated")
    val lastUpdated: Instant,

    @JsonSchema.Description("$INTEGRATION_TYPES `unknown` is a kind Digdir has added that this API does not know yet.")
    @SerialName("allowed_integration_types")
    val allowedIntegrationTypes: List<IntegrationType>,

    @JsonSchema.Description("The organisation that owns the scope: Kartverket.")
    @SerialName("owner_orgno")
    val ownerOrgno: OrganizationNumber,

    @JsonSchema.Description("$TOKEN_TYPES\n- `UNKNOWN`: a kind Digdir has added that this API does not know yet.")
    @SerialName("token_type")
    val tokenType: TokenType,

    @JsonSchema.Description("$VISIBILITIES\n- `UNKNOWN`: a value Digdir has added that this API does not know yet.")
    val visibility: Visibility,

    @JsonSchema.Description("$PERSONS_ONLY a person must consent before a client gets a token.")
    @SerialName("requires_user_consent")
    val requiresUserConsent: Boolean,

    @JsonSchema.Description("$PERSONS_ONLY a person must log in again, even with an active session.")
    @SerialName("requires_user_authentication")
    val requiresUserAuthentication: Boolean,

    @JsonSchema.Description("$PERSONS_ONLY tokens leave out the person's national identity number.")
    @SerialName("requires_pseudonymous_tokens")
    val requiresPseudonymousTokens: Boolean,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description(
        "Where an organisation with access can delegate it to a supplier, such as `https://www.altinn.no/`. Left " +
            "out when the scope cannot be delegated.",
    )
    @SerialName("delegation_source")
    val delegationSource: String? = null,

    @JsonSchema.Description("The most seconds an access token for the scope can live. `0` means Digdir's default.")
    @SerialName("at_max_age")
    val atMaxAge: Long,

    @JsonSchema.Description("The most seconds an authorization for the scope can live. `0` means Digdir's default.")
    @SerialName("authorization_max_lifetime")
    val authorizationMaxLifetime: Long,

    @JsonSchema.Description("If `true`, every organisation can get tokens for the scope without being given access.")
    @SerialName("accessible_for_all")
    val accessibleForAll: Boolean,

    @JsonSchema.Type(JsonType.STRING)
    @JsonSchema.Description("Digdir does not document this field.")
    @SerialName("enforced_aud_for_access_token")
    val enforcedAudForAccessToken: String? = null,

    @JsonSchema.Description("Digdir does not document this field.")
    val protected: Boolean,

    @JsonSchema.Description(
        "Digdir does not document this field. Its name matches Digdir's feature that lets European businesses get " +
            "tokens with an eIDAS seal, without being given access.",
    )
    @SerialName("supports_european_businesses")
    val supportsEuropeanBusinesses: Boolean,
) {
    companion object {
        fun from(scope: ClientScopeDetails) = ScopeDetails(
            name = scope.name,
            prefix = scope.prefix,
            subscope = scope.subscope,
            description = scope.description,
            longDescription = scope.longDescription,
            descriptionEn = scope.descriptionEn,
            descriptionNn = scope.descriptionNn,
            descriptionSe = scope.descriptionSe,
            longDescriptionEn = scope.longDescriptionEn,
            longDescriptionNn = scope.longDescriptionNn,
            longDescriptionSe = scope.longDescriptionSe,
            active = scope.active,
            created = scope.created,
            lastUpdated = scope.lastUpdated,
            allowedIntegrationTypes = scope.allowedIntegrationTypes,
            ownerOrgno = scope.ownerOrgno,
            tokenType = scope.tokenType,
            visibility = scope.visibility,
            requiresUserConsent = scope.requiresUserConsent,
            requiresUserAuthentication = scope.requiresUserAuthentication,
            requiresPseudonymousTokens = scope.requiresPseudonymousTokens,
            delegationSource = scope.delegationSource,
            atMaxAge = scope.atMaxAge,
            authorizationMaxLifetime = scope.authorizationMaxLifetime,
            accessibleForAll = scope.accessibleForAll,
            enforcedAudForAccessToken = scope.enforcedAudForAccessToken,
            protected = scope.protected,
            supportsEuropeanBusinesses = scope.supportsEuropeanBusinesses,
        )
    }
}

internal const val INTEGRATION_TYPES = "The kinds of client that can use the scope. A Maskinporten client needs " +
    "`maskinporten`, or an empty list, which allows every kind."

internal const val TOKEN_TYPES = """The kind of access token issued for the scope.

- `SELF_CONTAINED`: a JWT that the API can verify itself. Maskinporten only issues these.
- `OPAQUE`: a token that the API has to look up with Digdir."""

internal const val VISIBILITIES = """Who can see the scope.

- `PUBLIC`: anyone, in Digdir's list of all scopes.
- `PRIVATE`: only the owner and the organisations given access.
- `INTERNAL`: Digdir's own use."""

private const val PERSONS_ONLY = "Only for logins with ID-porten, not for Maskinporten:"
