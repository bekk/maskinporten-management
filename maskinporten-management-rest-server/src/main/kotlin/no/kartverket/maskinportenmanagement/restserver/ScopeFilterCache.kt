package no.kartverket.maskinportenmanagement.restserver

import java.time.Clock
import java.time.Duration
import java.time.Instant

// Bounded, since a caller can make up any number of different headers
internal class ScopeFilterCache(private val duration: Duration, private val clock: Clock) {
    private class Entry(val filter: ScopeFilter, val expiresAt: Instant)

    private val entries = object : LinkedHashMap<String, Entry>() {
        override fun removeEldestEntry(eldest: Map.Entry<String, Entry>) = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(clientCertificate: String): ScopeFilter? {
        val entry = entries[clientCertificate] ?: return null
        if (clock.instant() < entry.expiresAt) return entry.filter
        entries.remove(clientCertificate)
        return null
    }

    @Synchronized
    fun put(clientCertificate: String, filter: ScopeFilter) {
        if (duration.isZero) return
        entries[clientCertificate] = Entry(filter, clock.instant() + duration)
    }

    companion object {
        const val MAX_ENTRIES = 1000
    }
}
