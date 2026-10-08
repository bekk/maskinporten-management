package no.kartverket.maskinportenmanagement.restserver

import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalJwtGrantSignerTest {

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val keyFile: File = createTempFile(suffix = ".key").toFile().apply {
        writeText(
            "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded) +
                "\n-----END PRIVATE KEY-----\n",
        )
    }

    @AfterTest
    fun delete() {
        keyFile.delete()
    }

    @Test
    fun `signs RS256 with the key from the PEM file`() = runBlocking {
        val input = "header.claims".toByteArray()

        val signature = LocalJwtGrantSigner.fromPemFile(keyFile.path).sign(input)

        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(keyPair.public)
            update(input)
        }
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `refuses a file that holds no PKCS#8 private key`() {
        keyFile.writeText("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n")

        val e = assertFailsWith<IllegalStateException> { LocalJwtGrantSigner.fromPemFile(keyFile.path) }

        assertContains(e.message!!, "PKCS#8")
    }
}
