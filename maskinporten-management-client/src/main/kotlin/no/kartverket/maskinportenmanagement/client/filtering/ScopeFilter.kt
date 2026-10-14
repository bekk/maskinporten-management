package no.kartverket.maskinportenmanagement.client.filtering

import kotlinx.serialization.Serializable
import no.kartverket.maskinportenmanagement.client.Scope

@Serializable
public data class ScopeFilter(
    public val exact: List<String>,
    public val prefix: List<String>,
) {
    init {
        require(prefix.none(String::isBlank)) { "A prefix must not be blank" }
    }

    public fun allows(scope: Scope): Boolean = scope.value in exact || prefix.any { scope.value.startsWith(it) }
}
