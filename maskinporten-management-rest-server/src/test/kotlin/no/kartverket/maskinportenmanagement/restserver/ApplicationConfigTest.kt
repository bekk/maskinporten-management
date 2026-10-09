package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.config.yaml.YamlConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class ApplicationConfigTest {

    @Test
    fun `the port default is a port number`() {
        val config = YamlConfig("application.yaml") ?: fail("application.yaml is not on the classpath")
        config.port("server.port")
    }

    @Test
    fun `the Digdir base URL has no default, so a missing one names the variable to set`() {
        assumeTrue(System.getenv("DIGDIR_BASE_URL") == null, "DIGDIR_BASE_URL is set in this shell")
        val config = YamlConfig("application.yaml") ?: fail("application.yaml is not on the classpath")

        val e = assertFailsWith<IllegalStateException> { config.url("digdir.baseUrl") }

        assertContains(e.message!!, "digdir.baseUrl")
    }

    @Test
    fun `filtering is on by default`() {
        assumeTrue(System.getenv("EXTERNAL_FILTERING_ENABLED") == null, "EXTERNAL_FILTERING_ENABLED is set in this shell")
        val config = YamlConfig("application.yaml") ?: fail("application.yaml is not on the classpath")

        assertTrue(config.boolean("externalFiltering.enabled", default = true))
    }
}
