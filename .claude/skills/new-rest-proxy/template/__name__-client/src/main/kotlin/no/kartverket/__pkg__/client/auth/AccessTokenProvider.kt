package no.kartverket.__pkg__.client.auth

/**
 * Gives the token the client sends to __Upstream__. How it gets one depends on __Upstream__, so each project writes its
 * own, for example a Maskinporten JWT grant. It should reuse a token until shortly before it expires, and throw a
 * [no.kartverket.__pkg__.client.exception.TokenException] or an [java.io.IOException] when it cannot get one.
 */
public fun interface AccessTokenProvider {
    public suspend fun accessToken(): AccessToken

    /** __Upstream__ refused [token], so the next call must not get it again. */
    public suspend fun refused(token: AccessToken) {}
}
