package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

class ApplicationConfigTest {

    @Test
    fun `the maskinporten api base url has no default`() {
        assertFails { loadConfig(without = "MASKINPORTEN_API_BASE_URL") }
    }

    @Test
    fun `the maskinporten api base url is read from the environment`() {
        assertEquals("http://localhost:8080/api/v1", loadConfig().required("maskinporten.apiBaseUrl"))
    }

    @Test
    fun `the port default is a port number`() {
        loadConfig().port("server.port")
    }

    private fun loadConfig(without: String? = null): ApplicationConfig {
        val required = mapOf(
            "MASKINPORTEN_API_BASE_URL" to "http://localhost:8080/api/v1",
        ).filterKeys { it != without }
        required.forEach { (name, value) -> System.setProperty(name, value) }
        try {
            return YamlConfig("application.yaml") ?: fail("application.yaml is not on the classpath")
        } finally {
            required.keys.forEach { System.clearProperty(it) }
        }
    }
}
