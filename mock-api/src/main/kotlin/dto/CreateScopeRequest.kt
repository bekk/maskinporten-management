package com.maskinporten.dto

import com.maskinporten.models.scopes.IntegrationType
import com.maskinporten.models.scopes.Scope
import com.maskinporten.models.scopes.TokenType
import com.maskinporten.models.scopes.Visibility
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class CreateScopeRequest(
    val prefix: String,
    val subscope: String,
    val description: String,
    val long_description: String? = null,
    val active: Boolean,
    val owner_orgno: String? = null,
    val allowed_integration_types: List<IntegrationType>,
    val visibility: Visibility,
    val delegation_source: String? = null,
    val token_type: TokenType,
    val at_max_age: Long,
    val authorization_max_lifetime: Long? = null,
    val requires_user_authentication: Boolean,
    val requires_pseudonymous_tokens: Boolean,
    val requires_user_consent: Boolean,
    val accessible_for_all: Boolean,
    val enforced_aud_for_access_token: String? = null,
    val protected: Boolean? = null,
    val supports_european_businesses: Boolean? = null
)

fun CreateScopeRequest.toScope(): Scope {
    val prefix = this.prefix
    val subscope = this.subscope
    val key = "$prefix:$subscope"

    return Scope(
        prefix = prefix,
        description = description,
        subscope = subscope,
        long_description = long_description,
        descriptions = emptyMap(),
        active = active,
        created = Clock.System.now(),
        last_updated = Clock.System.now(),
        allowed_integration_types = allowed_integration_types,
        owner_orgno = owner_orgno ?: "",
        token_type = token_type,
        visibility = visibility,
        requires_user_consent = requires_user_consent,
        requires_user_authentication = requires_user_authentication,
        requires_pseudonymous_tokens = requires_pseudonymous_tokens,
        delegation_source = delegation_source,
        at_max_age = at_max_age,
        authorization_max_lifetime = authorization_max_lifetime ?: 0,
        accessible_for_all = accessible_for_all,
        enforced_aud_for_access_token = enforced_aud_for_access_token,
        name = key,
        protected = protected ?: false,
        supports_european_businesses = supports_european_businesses ?: false
    )
}

