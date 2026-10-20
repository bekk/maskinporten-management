package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
public data class ScopeDetails(
    public val name: Scope,
    public val prefix: String,
    public val subscope: String,
    public val description: String,
    @SerialName("long_description") public val longDescription: String? = null,
    @SerialName("description#en") public val descriptionEn: String? = null,
    @SerialName("description#nn") public val descriptionNn: String? = null,
    @SerialName("description#se") public val descriptionSe: String? = null,
    @SerialName("long_description#en") public val longDescriptionEn: String? = null,
    @SerialName("long_description#nn") public val longDescriptionNn: String? = null,
    @SerialName("long_description#se") public val longDescriptionSe: String? = null,
    public val active: Boolean,
    public val created: Instant,
    @SerialName("last_updated") public val lastUpdated: Instant,
    @SerialName("allowed_integration_types")
    public val allowedIntegrationTypes: List<@Serializable(with = IntegrationTypeSerializer::class) IntegrationType>,
    @SerialName("owner_orgno") public val ownerOrgno: OrganizationNumber,
    @SerialName("token_type") @Serializable(with = TokenTypeSerializer::class) public val tokenType: TokenType,
    @Serializable(with = VisibilitySerializer::class) public val visibility: Visibility,
    @SerialName("requires_user_consent") public val requiresUserConsent: Boolean,
    @SerialName("requires_user_authentication") public val requiresUserAuthentication: Boolean,
    @SerialName("requires_pseudonymous_tokens") public val requiresPseudonymousTokens: Boolean,
    @SerialName("delegation_source") public val delegationSource: String? = null,
    @SerialName("at_max_age") public val atMaxAge: Long,
    @SerialName("authorization_max_lifetime") public val authorizationMaxLifetime: Long,
    @SerialName("accessible_for_all") public val accessibleForAll: Boolean,
    @SerialName("enforced_aud_for_access_token") public val enforcedAudForAccessToken: String? = null,
    public val protected: Boolean,
    @SerialName("supports_european_businesses") public val supportsEuropeanBusinesses: Boolean,
)

@Serializable
public enum class IntegrationType {
    @SerialName("ansattporten")
    ANSATTPORTEN,

    @SerialName("api_klient")
    API_KLIENT,

    @SerialName("eformidling")
    EFORMIDLING,

    @SerialName("idporten")
    IDPORTEN,

    @SerialName("idporten_saml2")
    IDPORTEN_SAML2,

    @SerialName("krr")
    KRR,

    @SerialName("maskinporten")
    MASKINPORTEN,

    @SerialName("unknown")
    UNKNOWN,
}

internal object IntegrationTypeSerializer :
    OrUnknownSerializer<IntegrationType>(IntegrationType.serializer(), IntegrationType.entries, IntegrationType.UNKNOWN)

@Serializable
public enum class TokenType {
    SELF_CONTAINED,
    OPAQUE,
    UNKNOWN,
}

internal object TokenTypeSerializer :
    OrUnknownSerializer<TokenType>(TokenType.serializer(), TokenType.entries, TokenType.UNKNOWN)

@Serializable
public enum class Visibility {
    INTERNAL,
    PRIVATE,
    PUBLIC,
    UNKNOWN,
}

internal object VisibilitySerializer :
    OrUnknownSerializer<Visibility>(Visibility.serializer(), Visibility.entries, Visibility.UNKNOWN)
