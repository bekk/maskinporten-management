package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.config.yaml.YamlConfig
import kotlin.test.Test
import kotlin.test.fail

class ApplicationConfigTest {

    @Test
    fun `the port default is a port number`() {
        val config = YamlConfig("application.yaml") ?: fail("application.yaml is not on the classpath")
        config.port("server.port")
    }
}
