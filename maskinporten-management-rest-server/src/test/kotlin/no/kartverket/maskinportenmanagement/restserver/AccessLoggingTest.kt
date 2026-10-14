package no.kartverket.maskinportenmanagement.restserver

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AccessLoggingTest {

    private lateinit var appender: ListAppender<ILoggingEvent>

    private val root get() = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    @BeforeTest
    fun attachAppender() {
        appender = SnapshotAppender().apply { start() }
        root.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        root.detachAppender(appender)
    }

    private fun linesFor(enabled: String?) = run {
        testApplication {
            environment {
                config = if (enabled == null) {
                    MapApplicationConfig()
                } else {
                    MapApplicationConfig("accessLog.enabled" to enabled)
                }
            }
            application {
                configureAccessLogging()
                configureRouting()
            }
            client.get("/health/live")
        }
        appender.list.map { it.formattedMessage }
    }

    // Logs before and after moving to another thread, and fails, as the real routes can
    private fun eventsFor(enabled: String, certificate: String?) = run {
        testApplication {
            environment { config = MapApplicationConfig("accessLog.enabled" to enabled) }
            application {
                configureAccessLogging()
                configureSerialization()
                configureErrorHandling()
                routing {
                    get("/logs") {
                        call.application.log.info("before")
                        withContext(Dispatchers.IO) { call.application.log.info("on another thread") }
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/fails") { error("boom") }
                }
            }
            client.get("/logs") { certificate?.let { header(CLIENT_CERTIFICATE_HEADER, it) } }
            client.get("/fails") { certificate?.let { header(CLIENT_CERTIFICATE_HEADER, it) } }
        }
        appender.list
    }

    private fun List<ILoggingEvent>.fromCalls() = filter {
        it.loggerName == "access" || it.formattedMessage in setOf("before", "on another thread", "Unhandled exception")
    }

    private val certificate = "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"
    private val workload = "spiffe://cluster.local/ns/team/sa/app"

    @Test
    fun `logs one line per request by default, on its own logger so logback can route it apart from other logs`() {
        linesFor(null)
        val event = appender.list.single { it.formattedMessage.contains("/health/live") }

        assertEquals("access", event.loggerName)
    }

    @Test
    fun `logs nothing when switched off`() {
        val logged = linesFor("false")

        assertFalse(
            logged.any { it.contains("/health/live") },
            "expected no access log line, got: $logged",
        )
    }

    @Test
    fun `the workload is the SPIFFE ID of the caller`() {
        assertEquals(workload, workloadFrom(certificate))
    }

    @Test
    fun `the workload is the caller Istio added last, not one the caller sent itself`() {
        val certificate = "URI=spiffe://cluster.local/ns/fake/sa/app," +
            "By=spiffe://cluster.local/ns/a/sa/b;Hash=abc;Subject=\"\";URI=spiffe://cluster.local/ns/team/sa/app"

        assertEquals(workload, workloadFrom(certificate))
    }

    @Test
    fun `there is no workload without a caller`() {
        assertNull(workloadFrom(null))
        assertNull(workloadFrom(""))
        assertNull(workloadFrom("By=spiffe://cluster.local/ns/a/sa/b;Hash=abc"))
    }

    @Test
    fun `every line logged during a call has the workload`() {
        val events = eventsFor("true", certificate).fromCalls()

        assertEquals(5, events.size, "expected two lines, an error and two access lines, got: $events")
        events.forEach { assertEquals(workload, it.mdcPropertyMap["workload"], "for: $it") }
    }

    @Test
    fun `every line logged during a call has the workload, also with the access log off`() {
        val events = eventsFor("false", certificate).fromCalls()

        assertEquals(3, events.size, "expected two lines and an error, got: $events")
        events.forEach { assertEquals(workload, it.mdcPropertyMap["workload"], "for: $it") }
    }

    @Test
    fun `a call without a caller has no workload`() {
        val events = eventsFor("true", null).fromCalls()

        assertEquals(5, events.size, "expected two lines, an error and two access lines, got: $events")
        events.forEach { assertFalse("workload" in it.mdcPropertyMap, "for: $it") }
    }

    // Logback reads the MDC when the event is first asked for it, which here is after the call is over
    private class SnapshotAppender : ListAppender<ILoggingEvent>() {
        override fun append(e: ILoggingEvent) {
            e.prepareForDeferredProcessing()
            super.append(e)
        }
    }
}
