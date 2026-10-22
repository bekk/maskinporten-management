package no.kartverket.maskinportenmanagement.client

import no.kartverket.maskinportenmanagement.client.validation.ValidationCode
import no.kartverket.maskinportenmanagement.client.validation.ValidationError
import no.kartverket.maskinportenmanagement.client.validation.ValidationException
import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

@JvmInline
public value class ScopeJson private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        private val FIELDS = setOf(
            "prefix", "subscope", "description", "long_description", "active", "allowed_integration_types", "owner_orgno",
            "token_type", "visibility", "requires_user_consent", "requires_user_authentication",
            "requires_pseudonymous_tokens", "delegation_source", "at_max_age", "authorization_max_lifetime",
            "accessible_for_all", "enforced_aud_for_access_token", "protected", "supports_european_businesses",
            "description#en", "description#nn", "description#se",
            "long_description#en", "long_description#nn", "long_description#se",
        )

        private val NAME_FIELDS = listOf("prefix", "subscope")

        // Unlike kotlinx, Jackson can refuse a name given twice, so the access check in front of us and Digdir cannot
        // read different scopes from the body
        private val strictJson = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build()

        public fun of(value: String): ScopeJson {
            val json = try {
                strictJson.readTree(value)
            } catch (e: JacksonException) {
                throw IllegalArgumentException("The body is not valid JSON: ${e.originalMessage}")
            }
            require(json is ObjectNode) { "Expected a scope as a JSON object" }
            val errors = json.propertyNames().filter { it !in FIELDS }.map {
                ValidationError(it, ValidationCode.NOT_ALLOWED, "$it is not allowed")
            } + NAME_FIELDS.mapNotNull { nameFieldError(it, json[it]) }
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return ScopeJson(value)
        }

        private fun nameFieldError(field: String, node: JsonNode?): ValidationError? = when {
            node == null || node.isNull || (node.isString && node.stringValue().isBlank()) ->
                ValidationError(field, ValidationCode.MISSING, "$field is required")

            !node.isString -> ValidationError(field, ValidationCode.INVALID_FORMAT, "$field must be text")

            else -> null
        }
    }
}
