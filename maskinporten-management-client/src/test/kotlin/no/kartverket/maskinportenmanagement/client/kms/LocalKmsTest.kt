package no.kartverket.maskinportenmanagement.client.kms

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalKmsTest {

    private val keyPair = rsaKeyPair()

    @Test
    fun `signs with RS256, so the public key can verify the signature`() {
        val kms = LocalKms(keyPair.private as RSAPrivateCrtKey)
        val data = "header.payload".toByteArray()

        val signature = kms.sign(data)

        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(keyPair.public)
            update(data)
        }
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `reads an RSA private key from PKCS 8 PEM`() {
        val kms = LocalKms.fromPem(pem("PRIVATE KEY", keyPair.private.encoded))

        assertContentEquals(keyPair.public.encoded, kms.publicKey.encoded)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notRsaPrivateKeys")
    fun `refuses a PEM that is not an RSA private key`(kind: String, pem: String) {
        val e = assertFailsWith<IllegalArgumentException> { LocalKms.fromPem(pem) }

        assertContains(e.message!!, "PRIVATE KEY", ignoreCase = true)
    }

    companion object {
        @JvmStatic
        fun notRsaPrivateKeys(): List<Arguments> = listOf(
            Arguments.of("an EC private key", pem("PRIVATE KEY", KeyPairGenerator.getInstance("EC").generateKeyPair().private.encoded)),
            Arguments.of("an RSA public key", pem("PUBLIC KEY", rsaKeyPair().public.encoded)),
            Arguments.of("not a PEM", "not a key"),
        )
    }
}
