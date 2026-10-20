package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.config.yaml.YamlConfig
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class ApplicationConfigTest {

    @Test
    fun `the port defaults to 8080`() {
        assumeTrue(System.getenv("PORT") == null, "PORT is set in this shell")
        val config = YamlConfig("application.yaml") ?: fail("application.yaml is not on the classpath")

        assertEquals(8080, config.port("server.port"))
    }

    @Test
    fun `the Digdir environment has no default, so a missing one names the variable to set`() {
        assumeTrue(System.getenv("DIGDIR_ENVIRONMENT") == null, "DIGDIR_ENVIRONMENT is set in this shell")
        val config = YamlConfig("application.yaml") ?: fail("application.yaml is not on the classpath")

        val e = assertFailsWith<IllegalStateException> { config.enum<DigdirEnvironmentSetting>("digdir.environment") }

        assertContains(e.message!!, "digdir.environment")
    }
}
