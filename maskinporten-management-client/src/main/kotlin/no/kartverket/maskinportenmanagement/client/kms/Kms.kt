package no.kartverket.maskinportenmanagement.client.kms

import java.security.PublicKey

public interface Kms {
    public val publicKey: PublicKey

    // RS256: RSA PKCS #1 v1.5 over a SHA-256 digest of data
    public fun sign(data: ByteArray): ByteArray
}
