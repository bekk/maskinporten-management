package no.kartverket.maskinportenmanagement.client.kms

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

public class LocalKms(private val privateKey: RSAPrivateCrtKey) : Kms {
    override val publicKey: PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent))

    override fun sign(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }

    public companion object {
        public fun fromPem(pem: String): LocalKms {
            val key = try {
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pemContent(pem, "PRIVATE KEY")))
            } catch (e: InvalidKeySpecException) {
                throw IllegalArgumentException("Expected an RSA private key in PKCS #8 PEM", e)
            }
            require(key is RSAPrivateCrtKey) { "Expected an RSA private key in PKCS #8 PEM" }
            return LocalKms(key)
        }
    }
}
