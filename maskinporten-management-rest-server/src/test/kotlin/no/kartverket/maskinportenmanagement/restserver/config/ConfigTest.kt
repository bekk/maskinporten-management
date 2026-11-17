package no.kartverket.maskinportenmanagement.restserver.config

import io.ktor.server.config.MapApplicationConfig
import no.kartverket.maskinportenmanagement.client.DigdirEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {

    @Test
    fun `a misspelled environment names the valid values`() {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("digdir.environment" to "test").enum<DigdirEnvironment>("digdir.environment")
        }

        assertContains(e.message!!, "digdir.environment")
        assertContains(e.message!!, "TEST, PROD")
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
    fun `seconds are a whole number, 0 or more, and default when unset`() {
        assertEquals(Duration.ofSeconds(30), MapApplicationConfig("key" to "30").seconds("key", default = Duration.ZERO))
        assertEquals(Duration.ZERO, MapApplicationConfig("key" to "0").seconds("key", default = Duration.ofMinutes(1)))
        assertEquals(Duration.ofMinutes(1), MapApplicationConfig().seconds("key", default = Duration.ofMinutes(1)))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["-1", "1.5", "1m"])
    fun `seconds must be a whole number, 0 or more`(raw: String) {
        val e = assertFailsWith<IllegalStateException> { MapApplicationConfig("key" to raw).seconds("key", default = Duration.ZERO) }

        assertContains(e.message!!, "must be a whole number of seconds")
    }

    @Test
    fun `a blank required value counts as missing`() {
        val e = assertFailsWith<IllegalStateException> { MapApplicationConfig("key" to "   ").required("key") }

        assertContains(e.message!!, "Missing required configuration key")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["http", "0", "65536"])
    fun `a port must be a number from 1 to 65535`(raw: String) {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("server.port" to raw).port("server.port")
        }

        assertContains(e.message!!, "server.port")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["localhost:8080", "ftp://digdir.test", "https://", "not a url"])
    fun `a URL must be http or https and have a host`(raw: String) {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("externalFiltering.url" to raw).url("externalFiltering.url")
        }

        assertContains(e.message!!, "http or https URL")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["https://digdir.test?x=1", "https://digdir.test#top"])
    fun `a URL must not have a query or fragment`(raw: String) {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("externalFiltering.url" to raw).url("externalFiltering.url")
        }

        assertContains(e.message!!, "query or fragment")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["http://localhost:8080", "https://api.samarbeid.digdir.no", "HTTPS://api.samarbeid.digdir.no"])
    fun `an http or https URL is accepted as it is`(raw: String) {
        assertEquals(URI(raw), MapApplicationConfig("externalFiltering.url" to raw).url("externalFiltering.url"))
    }

    @Test
    fun `refuses a logback config path that is not a file`() {
        val e = assertFailsWith<IllegalStateException> {
            MapApplicationConfig("logback.configFile" to "/no/such/logback.xml").file("logback.configFile")
        }

        assertContains(e.message!!, "/no/such/logback.xml")
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
