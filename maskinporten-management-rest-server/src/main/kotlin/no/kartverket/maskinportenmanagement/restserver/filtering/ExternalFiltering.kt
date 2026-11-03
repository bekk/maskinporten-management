package no.kartverket.maskinportenmanagement.restserver.filtering

import java.time.Clock
import java.time.Duration

class ExternalFiltering(
    private val client: ExternalFilteringClient?,
    cacheDuration: Duration = Duration.ZERO,
    clock: Clock = Clock.systemUTC(),
) {
    private val cache = ScopeFilterCache(cacheDuration, clock)

    val isOn: Boolean get() = client != null

    suspend fun filterFor(certificate: String?): ScopeFilter? {
        val client = client ?: return null
        checkNotNull(certificate)
        cache.get(certificate)?.let { return it }
        return client.filterFor(certificate).also { cache.put(certificate, it) }
    }
}
