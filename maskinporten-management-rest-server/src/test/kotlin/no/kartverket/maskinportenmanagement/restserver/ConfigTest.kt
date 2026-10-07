package no.kartverket.maskinportenmanagement.restserver

import io.ktor.server.config.MapApplicationConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {

    private enum class Color { RED, GREEN }

    @Test
    fun `a misspelled enum value names the valid values`() {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("color" to "red").enum<Color>("color")
        }

        assertContains(e.message!!, "color")
        assertContains(e.message!!, "RED, GREEN")
    }

    @Test
    fun `every value is trimmed the same way`() {
        val config = MapApplicationConfig("key" to "  value  ", "flag" to " true ")

        assertEquals("value", config.required("key"))
        assertEquals(true, config.boolean("flag", default = false))
    }

    @Test
    fun `a flag must be true or false`() {
        val e = assertFailsWith<IllegalStateException> { MapApplicationConfig("flag" to "yes").boolean("flag", default = true) }

        assertContains(e.message!!, "must be true or false")
    }

    @Test
    fun `a blank required value counts as missing`() {
        val e = assertFailsWith<IllegalStateException> { MapApplicationConfig("key" to "   ").required("key") }

        assertContains(e.message!!, "Missing required configuration key")
    }

    @Test
    fun `a port must be a number from 1 to 65535`() {
        for (raw in listOf("http", "0", "65536")) {
            val e = assertFailsWith<IllegalStateException>(raw) {
                MapApplicationConfig("server.port" to raw).port("server.port")
            }

            assertContains(e.message!!, "server.port", message = raw)
        }
    }

    @Test
    fun `a URL must be http or https and have a host`() {
        for (raw in listOf("localhost:8080", "ftp://digdir.test", "https://", "not a url")) {
            val e = assertFailsWith<IllegalStateException>(raw) {
                MapApplicationConfig("digdir.baseUrl" to raw).url("digdir.baseUrl")
            }

            assertContains(e.message!!, "http or https URL", message = raw)
        }
    }

    @Test
    fun `a URL must not have a query or fragment`() {
        for (raw in listOf("https://digdir.test?x=1", "https://digdir.test#top")) {
            val e = assertFailsWith<IllegalStateException>(raw) {
                MapApplicationConfig("digdir.baseUrl" to raw).url("digdir.baseUrl")
            }

            assertContains(e.message!!, "query or fragment", message = raw)
        }
    }

    @Test
    fun `an http or https URL is accepted as it is`() {
        for (raw in listOf("http://localhost:8080", "https://api.samarbeid.digdir.no", "HTTPS://api.samarbeid.digdir.no")) {
            assertEquals(raw, MapApplicationConfig("digdir.baseUrl" to raw).url("digdir.baseUrl"))
        }
    }

    @Test
    fun `loads the application config from the given file`() {
        val file = File.createTempFile("application", ".yaml").apply { deleteOnExit() }
        file.writeText("server:\n  port: \"9090\"\n")

        assertEquals(9090, loadApplicationConfig(file.path).port("server.port"))
    }

    @Test
    fun `refuses an application config path that is not a file`() {
        val e = assertFailsWith<IllegalStateException> { loadApplicationConfig("/no/such/application.yaml") }

        assertContains(e.message!!, "/no/such/application.yaml")
    }

    @Test
    fun `refuses an application config file that is not yaml`() {
        val file = File.createTempFile("application", ".conf").apply { deleteOnExit() }

        val e = assertFailsWith<IllegalStateException> { loadApplicationConfig(file.path) }

        assertContains(e.message!!, ".yaml or .yml")
    }
}
