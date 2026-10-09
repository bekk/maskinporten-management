package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only documents Digdir's scopes in the OpenAPI spec; each scope is passed on unchanged
@Serializable
data class Scope(
    val name: String,
    val prefix: String,
    val subscope: String,
    val description: String,
    @SerialName("long_description") val longDescription: String? = null,
    val descriptions: Map<String, String>? = null,
    val active: Boolean,
    val created: String,
    @SerialName("last_updated") val lastUpdated: String,
    @SerialName("allowed_integration_types") val allowedIntegrationTypes: List<IntegrationType>,
    @SerialName("owner_orgno") val ownerOrgno: String,
    @SerialName("token_type") val tokenType: TokenType,
    val visibility: Visibility,
    @SerialName("requires_user_consent") val requiresUserConsent: Boolean,
    @SerialName("requires_user_authentication") val requiresUserAuthentication: Boolean,
    @SerialName("requires_pseudonymous_tokens") val requiresPseudonymousTokens: Boolean,
    @SerialName("delegation_source") val delegationSource: String? = null,
    @SerialName("at_max_age") val atMaxAge: Long,
    @SerialName("authorization_max_lifetime") val authorizationMaxLifetime: Long,
    @SerialName("accessible_for_all") val accessibleForAll: Boolean,
    @SerialName("enforced_aud_for_access_token") val enforcedAudForAccessToken: String? = null,
    val protected: Boolean,
    @SerialName("supports_european_businesses") val supportsEuropeanBusinesses: Boolean,
)

@Serializable
enum class IntegrationType {
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
}

@Serializable
enum class TokenType {
    SELF_CONTAINED,
    OPAQUE,
}

@Serializable
enum class Visibility {
    INTERNAL,
    PRIVATE,
    PUBLIC,
}
