package no.kartverket.__pkg__.restserver

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import no.kartverket.__pkg__.client.__Upstream__Environment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.fail

class ApplicationConfigTest {

    @Test
    fun `the __upstream__ environment has no default`() {
        assertFails { loadConfig(without = "__UPSTREAM___ENVIRONMENT") }
    }

    @Test
    fun `the __upstream__ environment is read from its variable`() {
        assertEquals(__Upstream__Environment.TEST, loadConfig().enum<__Upstream__Environment>("__upstream__.environment"))
    }

    @Test
    fun `the port default is a port number`() {
        loadConfig().port("server.port")
    }

    private fun loadConfig(without: String? = null): ApplicationConfig {
        val required = mapOf(
            "__UPSTREAM___ENVIRONMENT" to "TEST",
        ).filterKeys { it != without }
        required.forEach { (name, value) -> System.setProperty(name, value) }
        try {
            return YamlConfig("application.yaml") ?: fail("application.yaml is not on the classpath")
        } finally {
            required.keys.forEach { System.clearProperty(it) }
        }
    }
}
