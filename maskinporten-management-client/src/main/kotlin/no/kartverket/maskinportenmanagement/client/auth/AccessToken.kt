package no.kartverket.maskinportenmanagement.client.auth

private val BEARER_TOKEN = Regex("[A-Za-z0-9._~+/-]+=*")

internal fun isValidBearerToken(value: String): Boolean = BEARER_TOKEN.matches(value)

@JvmInline
public value class AccessToken private constructor(public val value: String) {
    // Hides the token, so a log line or exception message that includes it does not leak it
    override fun toString(): String = "AccessToken(***)"

    public companion object {
        public fun of(value: String): AccessToken = AccessToken(value)
    }
}
