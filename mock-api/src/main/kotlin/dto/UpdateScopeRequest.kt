package com.maskinporten.dto

import com.maskinporten.models.scopes.IntegrationType
import com.maskinporten.models.scopes.TokenType
import com.maskinporten.models.scopes.Visibility
import com.maskinporten.models.scopes.collectTranslations
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = UpdateScopeRequestSerializer::class)
data class UpdateScopeRequest(
    val prefix: String? = null,
    val subscope: String? = null,
    val description: String? = null,
    val long_description: String? = null,
    /** description#en, long_description#nn and so on */
    val descriptions: Map<String, String> = emptyMap(),
    val active: Boolean? = null,
    val owner_orgno: String? = null,
    val allowed_integration_types: List<IntegrationType>? = null,
    val visibility: Visibility? = null,
    val delegation_source: String? = null,
    val token_type: TokenType? = null,
    val at_max_age: Long? = null,
    val authorization_max_lifetime: Long? = null,
    val requires_user_authentication: Boolean? = null,
    val requires_pseudonymous_tokens: Boolean? = null,
    val requires_user_consent: Boolean? = null,
    val accessible_for_all: Boolean? = null,
    val enforced_aud_for_access_token: String? = null,
    val protected: Boolean? = null,
    val supports_european_businesses: Boolean? = null
)

@OptIn(ExperimentalSerializationApi::class)
object UpdateScopeRequestSerializer : JsonTransformingSerializer<UpdateScopeRequest>(UpdateScopeRequest.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement) = collectTranslations(element)
}
