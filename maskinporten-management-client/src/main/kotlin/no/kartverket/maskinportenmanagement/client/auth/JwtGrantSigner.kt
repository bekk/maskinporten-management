package no.kartverket.maskinportenmanagement.client.auth

/**
 * Signs a JWT grant with the private key of the business certificate. Kept apart from [MaskinportenTokenProvider] so
 * the key can live where it cannot be copied, such as Cloud KMS.
 */
public fun interface JwtGrantSigner {
    /** Returns the RS256 signature (RSASSA-PKCS1-v1_5 with SHA-256) of [signingInput]. */
    public suspend fun sign(signingInput: ByteArray): ByteArray
}
