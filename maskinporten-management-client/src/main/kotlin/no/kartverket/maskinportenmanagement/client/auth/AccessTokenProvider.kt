package no.kartverket.maskinportenmanagement.client.auth

public fun interface AccessTokenProvider {
    public suspend fun accessToken(): String

    /** Digdir refused [token], so the next call must not get it again. */
    public suspend fun refused(token: String) {}
}
