package no.kartverket.maskinportenmanagement.restserver

import kotlinx.serialization.Serializable
import no.kartverket.maskinportenmanagement.client.Scope

@Serializable
data class ScopeFilter(
    val exact: List<String>,
    val prefix: List<String>,
) {
    init {
        require(prefix.none(String::isBlank)) { "A prefix must not be blank" }
    }

    fun allows(scope: Scope): Boolean = scope.value in exact || prefix.any { scope.value.startsWith(it) }
}

internal fun <T> List<T>.allowedBy(filter: ScopeFilter?, scope: (T) -> Scope): List<T> =
    if (filter == null) this else filter { filter.allows(scope(it)) }
