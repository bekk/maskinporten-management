package no.kartverket.maskinportenmanagement.restserver

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.auth.MaskinportenTokenProvider
import no.kartverket.maskinportenmanagement.client.filtering.ExternalFilteringClient.Companion.CLIENT_CERTIFICATE_HEADER
import no.kartverket.maskinportenmanagement.client.kms.LocalKms
import no.kartverket.maskinportenmanagement.client.kms.certificateChain
import no.kartverket.maskinportenmanagement.client.kms.pem
import no.kartverket.maskinportenmanagement.client.kms.rsaKeyPair
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPrivateCrtKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoggingTest {

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

    private fun linesFor(accessLog: Boolean?) = run {
        testApplication {
            application {
                if (accessLog == null) {
                    module(testClient(), ExternalFiltering(null))
                } else {
                    module(testClient(), ExternalFiltering(null), accessLog)
                }
            }
            client.get("/health/live")
        }
        appender.list.map { it.formattedMessage }
    }

    // Logs before and after moving to another thread, and fails, as the real routes can
    private fun eventsFor(accessLog: Boolean, certificate: String?, requestId: String? = null) = run {
        val headers: HttpRequestBuilder.() -> Unit = {
            certificate?.let { header(CLIENT_CERTIFICATE_HEADER, it) }
            requestId?.let { header(HttpHeaders.XRequestId, it) }
        }
        testApplication {
            application {
                module(testClient(), ExternalFiltering(null), accessLog)
                routing {
                    get("/logs") {
                        call.application.log.info("before")
                        withContext(Dispatchers.IO) { call.application.log.info("on another thread") }
                        call.respond(HttpStatusCode.OK)
                    }
                    get("/fails") { error("boom") }
                }
            }
            client.get("/logs", headers)
            client.get("/fails", headers)
        }
        appender.list
    }

    private val ILoggingEvent.requestId get() = mdcPropertyMap["request_id"]

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
        val logged = linesFor(false)

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
    fun `the workload is found also with spaces between the fields`() {
        assertEquals(
            workload,
            workloadFrom("URI=spiffe://cluster.local/ns/old/sa/app, URI=spiffe://cluster.local/ns/team/sa/app"),
        )
    }

    @Test
    fun `there is no workload without a caller`() {
        assertNull(workloadFrom(null))
        assertNull(workloadFrom(""))
        assertNull(workloadFrom("By=spiffe://cluster.local/ns/a/sa/b;Hash=abc"))
    }

    @Test
    fun `every line logged during a call has the workload`() {
        val events = eventsFor(true, certificate).fromCalls()

        assertEquals(5, events.size, "expected two lines, an error and two access lines, got: $events")
        events.forEach { assertEquals(workload, it.mdcPropertyMap["workload"], "for: $it") }
    }

    @Test
    fun `every line logged during a call has the workload, also with the access log off`() {
        val events = eventsFor(false, certificate).fromCalls()

        assertEquals(3, events.size, "expected two lines and an error, got: $events")
        events.forEach { assertEquals(workload, it.mdcPropertyMap["workload"], "for: $it") }
    }

    @Test
    fun `a call without a caller has no workload`() {
        val events = eventsFor(true, null).fromCalls()

        assertEquals(5, events.size, "expected two lines, an error and two access lines, got: $events")
        events.forEach { assertFalse("workload" in it.mdcPropertyMap, "for: $it") }
    }

    @Test
    fun `every line logged during a call has the request id the caller sent`() {
        val events = eventsFor(true, certificate, requestId = "3f2a1c9e-7b4d-4e8f-9a6b-2c1d0e9f8a7b").fromCalls()

        assertEquals(5, events.size, "expected two lines, an error and two access lines, got: $events")
        events.forEach { assertEquals("3f2a1c9e-7b4d-4e8f-9a6b-2c1d0e9f8a7b", it.requestId, "for: $it") }
    }

    @Test
    fun `a call without a request id gets a new one, the same on every line it logs`() {
        val events = eventsFor(true, certificate).fromCalls()

        val (logs, fails) = events.partition { it.formattedMessage in setOf("before", "on another thread") || "/logs" in it.formattedMessage }
        val logsId = assertNotNull(logs.map { it.requestId }.distinct().single(), "for: $logs")
        val failsId = assertNotNull(fails.map { it.requestId }.distinct().single(), "for: $fails")
        assertNotEquals(logsId, failsId)
    }

    @Test
    fun `a request id that is not safe to log is replaced by a new one`() {
        val events = eventsFor(true, certificate, requestId = "id\" level=ERROR").fromCalls()

        assertEquals(5, events.size, "expected two lines, an error and two access lines, got: $events")
        events.forEach {
            assertNotNull(it.requestId, "for: $it")
            assertNotEquals("id\" level=ERROR", it.requestId, "for: $it")
        }
    }

    @Test
    fun `the response has the request id, so the caller can find the call in our logs`() {
        val replied = mutableListOf<String?>()
        testApplication {
            application { module(testClient(), ExternalFiltering(null)) }
            replied += client.get("/health/live") { header(HttpHeaders.XRequestId, "abc-123") }.headers[HttpHeaders.XRequestId]
            replied += client.get("/health/live").headers[HttpHeaders.XRequestId]
        }
        val logged = appender.list.filter { it.loggerName == "access" }.map { it.requestId }

        assertEquals("abc-123", replied[0])
        assertEquals(logged, replied)
    }

    @Test
    fun `a request id may only have letters, digits and a few separators, and at most 128 of them`() {
        assertTrue(isSafeRequestId("3f2a1c9e-7b4d-4e8f-9a6b-2c1d0e9f8a7b"))
        assertTrue(isSafeRequestId("team.app_1:42"))
        assertTrue(isSafeRequestId("a".repeat(128)))
        assertFalse(isSafeRequestId(""))
        assertFalse(isSafeRequestId("a".repeat(129)))
        assertFalse(isSafeRequestId("line\nbreak"))
        assertFalse(isSafeRequestId("with space"))
        assertFalse(isSafeRequestId("blåbær"))
    }

    @Test
    fun `the lines of a token fetch have the workload and request id of the call that waits for it`() {
        val keyPair = rsaKeyPair()
        val maskinporten = LoggingOutgoingHttpClient { request ->
            when (request.url.path) {
                "/.well-known/oauth-authorization-server" ->
                    json(200, """{"issuer":"https://maskinporten.test/","token_endpoint":"https://maskinporten.test/token"}""")

                else -> json(200, """{"access_token":"test-token","expires_in":120}""")
            }
        }
        val tokenProvider = MaskinportenTokenProvider(
            wellKnownUrl = URI("https://maskinporten.test/.well-known/oauth-authorization-server"),
            clientId = "test-client",
            scopes = listOf(Scope.of("idporten:scopes.read")),
            kms = LocalKms(keyPair.private as RSAPrivateCrtKey),
            certificateChainPem = certificateChain(keyPair.public).joinToString("") { pem(it) },
            httpClient = maskinporten,
        )
        testApplication {
            application {
                module(testClient(), ExternalFiltering(null))
                routing {
                    get("/token") {
                        tokenProvider.accessToken()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
            client.get("/token") {
                header(CLIENT_CERTIFICATE_HEADER, certificate)
                header(HttpHeaders.XRequestId, "token-call")
            }
        }
        tokenProvider.close()

        val events = appender.list.filter { it.loggerName == "outgoing" }
        assertEquals(4, events.size, "expected a call and an answer for the metadata and for the token, got: $events")
        events.forEach {
            assertEquals(workload, it.mdcPropertyMap["workload"], "for: $it")
            assertEquals("token-call", it.requestId, "for: $it")
        }
    }

    // Logback reads the MDC when the event is first asked for it, which here is after the call is over
    private class SnapshotAppender : ListAppender<ILoggingEvent>() {
        override fun append(e: ILoggingEvent) {
            e.prepareForDeferredProcessing()
            super.append(e)
        }
    }
}
