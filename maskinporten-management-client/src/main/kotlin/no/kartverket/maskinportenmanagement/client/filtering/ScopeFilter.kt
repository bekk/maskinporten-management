package no.kartverket.maskinportenmanagement.client.filtering

import kotlinx.serialization.Serializable

@Serializable
public data class ScopeFilter(
    public val exact: List<String>,
    public val prefix: List<String>,
) {
    init {
        require(prefix.none(String::isBlank)) { "A prefix must not be blank" }
    }

    public fun allows(scope: String): Boolean = scope in exact || prefix.any { scope.startsWith(it) }
}
