package no.kartverket.maskinportenmanagement.client.auth

import java.time.Duration
import java.time.Instant

internal sealed interface AccessToken {
    val value: String
    val expiresAt: Instant

    fun isExpired(now: Instant, leeway: Duration): Boolean = !now.plus(leeway).isBefore(expiresAt)
}

internal data class MaskinportenToken(override val value: String, override val expiresAt: Instant) : AccessToken {
    override fun toString(): String = "MaskinportenToken[value=***, expiresAt=$expiresAt]"
}
