package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFails

class MaskinportenManagementConfigTest {

    private val localKeyFile = createTempFile(suffix = ".pem").apply {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        writeText("-----BEGIN PRIVATE KEY-----\n${Base64.getMimeEncoder().encodeToString(key.encoded)}\n-----END PRIVATE KEY-----\n")
        toFile().deleteOnExit()
    }

    private val complete = mapOf(
        "digdir.baseUrl" to "http://localhost:8080",
        "maskinporten.wellKnownUrl" to "http://localhost:8080/.well-known/oauth-authorization-server",
        "maskinporten.clientId" to "dev-client",
        "maskinporten.scopes" to "idporten:scopes.write",
        "maskinporten.certificateChainFile" to "does-not-exist.pem",
        "kms.localKeyFile" to localKeyFile.toString(),
    )

    private fun startupFailure(settings: Map<String, String>): String {
        val e = assertFails {
            testApplication {
                environment { config = MapApplicationConfig(*settings.toList().toTypedArray()) }
                application { configureMaskinportenManagement() }
                startApplication()
            }
        }
        return generateSequence(e) { it.cause }.joinToString(" / ") { it.message.orEmpty() }
    }

    @Test
    fun `the server does not start without a Maskinporten setting, and names it`() {
        for (setting in listOf("maskinporten.wellKnownUrl", "maskinporten.clientId", "maskinporten.scopes", "maskinporten.certificateChainFile")) {
            assertContains(startupFailure(complete - setting), setting)
        }
    }

    @Test
    fun `the server does not start unless exactly one KMS is set`() {
        assertContains(startupFailure(complete - "kms.localKeyFile"), "exactly one")
        assertContains(startupFailure(complete + ("kms.keyVersion" to "projects/p/locations/l/keyRings/r/cryptoKeys/k/cryptoKeyVersions/1")), "exactly one")
    }

    @Test
    fun `the server does not start without the certificate chain file`() {
        assertContains(startupFailure(complete), "maskinporten.certificateChainFile must point to a PEM file")
    }
}
