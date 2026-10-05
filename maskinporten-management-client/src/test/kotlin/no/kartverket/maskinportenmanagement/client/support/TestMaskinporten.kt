package no.kartverket.maskinportenmanagement.client.support

import no.kartverket.maskinportenmanagement.client.auth.MaskinportenConfig
import no.kartverket.maskinportenmanagement.client.auth.MaskinportenKey

internal const val TOKEN_PATH = "/token"

internal val testMaskinportenKey: MaskinportenKey = MaskinportenKey.parse(TestKeys.rsa.toJSONString())

internal fun maskinportenConfig(
    tokenUrl: String = "https://test.maskinporten.no/token",
    clientId: String = "my-client-id",
) = MaskinportenConfig(tokenUrl, clientId, testMaskinportenKey)

internal fun maskinportenTokenResponse(accessToken: String = "mp-token", expiresIn: Long? = 3600): String =
    if (expiresIn == null) {
        """{"access_token":"$accessToken","token_type":"Bearer"}"""
    } else {
        """{"access_token":"$accessToken","token_type":"Bearer","expires_in":$expiresIn}"""
    }
