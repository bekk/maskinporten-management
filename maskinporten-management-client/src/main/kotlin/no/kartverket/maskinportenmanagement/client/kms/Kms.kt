package no.kartverket.maskinportenmanagement.client.kms

import java.security.PublicKey

public interface Kms {
    public val publicKey: PublicKey

    public fun sign(data: ByteArray): ByteArray
}
