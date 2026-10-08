package no.kartverket.maskinportenmanagement.client.kms

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
    fun `knows the public key that belongs to the private key`() {
        val kms = LocalKms(keyPair.private as RSAPrivateCrtKey)

        assertContentEquals(keyPair.public.encoded, kms.publicKey.encoded)
    }

    @Test
    fun `reads an RSA private key from PKCS 8 PEM`() {
        val kms = LocalKms.fromPem(pem("PRIVATE KEY", keyPair.private.encoded))

        assertContentEquals(keyPair.public.encoded, kms.publicKey.encoded)
    }

    @Test
    fun `refuses a PEM that is not an RSA private key`() {
        val ecKey = KeyPairGenerator.getInstance("EC").generateKeyPair().private

        for (pem in listOf(pem("PRIVATE KEY", ecKey.encoded), pem("PUBLIC KEY", keyPair.public.encoded), "not a key")) {
            val e = assertFailsWith<IllegalArgumentException>(pem) { LocalKms.fromPem(pem) }

            assertContains(e.message!!, "PRIVATE KEY", ignoreCase = true, message = pem)
        }
    }
}
