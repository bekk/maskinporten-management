package com.kartverket.backend.maskinporten.models

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.time.Instant

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class Scope(
    val name: String,
    val prefix: String,
    val description: String,
    val subscope: String,
    val longDescription: String?,
    val active: Boolean,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val created: Instant,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val lastUpdated: Instant,
    val allowedIntegrationTypes: List<IntegrationType>,
    val ownerOrgno: String?,
    val tokenType: TokenType,
    val visibility: Visibility,
    val requiresUserConsent: Boolean,
    val requiresUserAuthentication: Boolean,
    val requiresPseudonymousTokens: Boolean,
    val delegationSource: String?,
    val atMaxAge: Long,
    val authorizationMaxLifetime: Long?,
    val accessibleForAll: Boolean,
    val enforcedAudForAccessToken: String?,
    val protected: Boolean?,
    val supportsEuropeanBusinesses: Boolean,
    val descriptions: Map<String, String>?
)

enum class TokenType {
    SELF_CONTAINED,
    OPAQUE
}

enum class Visibility {
    INTERNAL,
    PRIVATE,
    PUBLIC
}

enum class IntegrationType(@get:JsonValue val value: String) {
    ANSATTPORTEN("ansattporten"),
    API_KLIENT("api_klient"),
    EFORMIDLING("eformidling"),
    IDPORTEN("idporten"),
    IDPORTEN_SAML2("idporten_saml2"),
    KRR("krr"),
    MASKINPORTEN("maskinporten");
}
