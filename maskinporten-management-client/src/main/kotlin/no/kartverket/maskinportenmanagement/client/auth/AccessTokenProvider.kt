package no.kartverket.maskinportenmanagement.client.auth

public fun interface AccessTokenProvider {
    public suspend fun accessToken(): String
}
