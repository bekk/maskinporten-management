package com.maskinporten.models.scopes

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = ScopeSerializer::class)
data class Scope(
    val prefix: String,
    val description: String,
    val subscope: String,
    val long_description: String? = null,
    val descriptions: Map<String, String>? = null,
    val active: Boolean,
    val created: Instant,
    val last_updated: Instant,
    val allowed_integration_types: List<IntegrationType>,
    val owner_orgno: String,
    val token_type: TokenType,
    val visibility: Visibility,
    val requires_user_consent: Boolean,
    val requires_user_authentication: Boolean,
    val requires_pseudonymous_tokens: Boolean,
    val delegation_source: String? = null,
    val at_max_age: Long,
    val authorization_max_lifetime: Long? = null,
    val accessible_for_all: Boolean,
    val enforced_aud_for_access_token: String? = null,
    val name: String,
    val protected: Boolean? = null,
    val supports_european_businesses: Boolean? = null
)

@OptIn(ExperimentalSerializationApi::class)
object ScopeSerializer : JsonTransformingSerializer<Scope>(Scope.generatedSerializer()) {
    override fun transformSerialize(element: JsonElement): JsonElement {
        val fields = element.jsonObject
        val translations = fields["descriptions"]?.jsonObject.orEmpty()
        return JsonObject(fields - "descriptions" + translations)
    }
}

private val TRANSLATED_FIELDS = setOf("description", "long_description")

/** The reverse of [ScopeSerializer]: requests send translations as fields like `description#en`, which go into `descriptions`. */
fun collectTranslations(element: JsonElement): JsonElement {
    val (translations, fields) = element.jsonObject.entries.partition { it.key.substringBefore('#', "") in TRANSLATED_FIELDS }
    return JsonObject(fields.associate { it.toPair() } + ("descriptions" to JsonObject(translations.associate { it.toPair() })))
}

enum class TokenType {
    SELF_CONTAINED,
    OPAQUE
}

enum class Visibility {
    INTERNAL,
    PRIVATE,
    PUBLIC
}

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
    MASKINPORTEN
}