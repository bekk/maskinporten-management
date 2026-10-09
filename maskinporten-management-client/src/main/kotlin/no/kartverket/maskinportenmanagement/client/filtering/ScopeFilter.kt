package no.kartverket.maskinportenmanagement.client.filtering

import kotlinx.serialization.Serializable

@Serializable
public data class ScopeFilter(
    public val exact: List<String>,
    public val prefix: List<String>,
) {
    public fun allows(scope: String): Boolean = scope in exact || prefix.any { scope.startsWith(it) }
}
