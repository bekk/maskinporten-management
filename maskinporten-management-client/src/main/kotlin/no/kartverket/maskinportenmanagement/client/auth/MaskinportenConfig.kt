package no.kartverket.maskinportenmanagement.client.auth

import java.net.URI

internal data class MaskinportenConfig(
    val tokenUrl: String,
    val clientId: String,
    val key: MaskinportenKey,
) {
    val audience: String = URI.create(tokenUrl).let { "${it.scheme}://${it.authority}/" }
}
