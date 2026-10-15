package no.kartverket.maskinportenmanagement.restserver

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import no.kartverket.maskinportenmanagement.client.kms.certificateChain
import no.kartverket.maskinportenmanagement.client.kms.pem
import no.kartverket.maskinportenmanagement.client.kms.rsaKeyPair
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFails
import kotlin.test.assertTrue

class MaskinportenManagementConfigTest {

    private val keyPair = rsaKeyPair()

    private fun pemFile(contents: String): Path = createTempFile(suffix = ".pem").apply {
        writeText(contents)
        toFile().deleteOnExit()
    }

    private val complete = mapOf(
        "digdir.environment" to "LOCAL",
        "maskinporten.clientId" to "dev-client",
        "digdirAdmin.certChain" to certificateChain(keyPair.public).joinToString("") { pem(it) },
        "digdirAdmin.localKeyPath" to pemFile(pem("PRIVATE KEY", keyPair.private.encoded)).toString(),
        "externalFiltering.url" to "http://localhost:8080/filtering",
    )

    private fun start(settings: Map<String, String>) = testApplication {
        environment { config = MapApplicationConfig(*settings.toList().toTypedArray()) }
        application { configureMaskinportenManagement() }
        startApplication()
    }

    private fun startupFailure(settings: Map<String, String>): String {
        val e = assertFails { start(settings) }
        return generateSequence(e) { it.cause }.joinToString(" / ") { it.message.orEmpty() }
    }

    @Test
    fun `the server starts with a complete configuration`() = start(complete)

    @Test
    fun `the server does not start without a Digdir or Maskinporten setting, and names it`() {
        val settings = listOf(
            "digdir.environment",
            "maskinporten.clientId",
            "digdirAdmin.certChain",
        )
        for (setting in settings) {
            assertContains(startupFailure(complete - setting), setting)
        }
    }

    @Test
    fun `filtering is off unless turned on, and then needs no URL`() = start(complete - "externalFiltering.url")

    @Test
    fun `turned on, filtering stops the server without its URL`() {
        val settings = complete - "externalFiltering.url" + ("externalFiltering.enabled" to "true")

        assertContains(startupFailure(settings), "externalFiltering.url")
    }

    @Test
    fun `the server warns at startup when filtering is off`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        try {
            start(complete)
        } finally {
            root.detachAppender(appender)
        }

        assertTrue(
            appender.list.any { it.level == Level.WARN && "External filtering is off" in it.formattedMessage },
            "expected a warning, got: ${appender.list.map { it.formattedMessage }}",
        )
    }

    @Test
    fun `the server does not start unless exactly one KMS is set`() {
        assertContains(startupFailure(complete - "digdirAdmin.localKeyPath"), "exactly one")
        assertContains(startupFailure(complete + ("digdirAdmin.gcpCloudKmsKeyPath" to "projects/p/locations/l/keyRings/r/cryptoKeys/k/cryptoKeyVersions/1")), "exactly one")
    }

    @Test
    fun `the server does not start without the local key file, and names the setting`() {
        assertContains(
            startupFailure(complete + ("digdirAdmin.localKeyPath" to "does-not-exist.key")),
            "digdirAdmin.localKeyPath must point to a PEM file",
        )
    }

    @Test
    fun `the server does not start unless the certificate chain is PEM, and names the setting`() {
        assertContains(
            startupFailure(complete + ("digdirAdmin.certChain" to "\$(cat virksomhetssertifikat-chain.pem)")),
            "digdirAdmin.certChain must be the certificate chain itself in PEM",
        )
        assertContains(
            startupFailure(complete + ("digdirAdmin.certChain" to "-----BEGIN CERTIFICATE-----\nnot-base64\n-----END CERTIFICATE-----\n")),
            "digdirAdmin.certChain must be the certificate chain itself in PEM",
        )
    }
}
