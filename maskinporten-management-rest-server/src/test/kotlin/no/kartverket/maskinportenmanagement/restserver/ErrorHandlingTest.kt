package no.kartverket.maskinportenmanagement.restserver

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.kartverket.maskinportenmanagement.client.DigdirErrorResponseException
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorHandlingTest {

    private lateinit var appender: ListAppender<ILoggingEvent>

    private val root get() = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

    @BeforeTest
    fun attachAppender() {
        appender = ListAppender<ILoggingEvent>().apply { start() }
        root.addAppender(appender)
    }

    @AfterTest
    fun detachAppender() {
        root.detachAppender(appender)
    }

    private fun answerTo(failure: Throwable, check: suspend (HttpResponse) -> Unit) = testApplication {
        application {
            module(testClient(), ExternalFiltering(null))
            routing { get("/fails") { throw failure } }
        }
        check(client.get("/fails"))
    }

    // Ktor's client cannot send a broken %-escape, so the check is tested on its own
    @Test
    fun `a URL with a broken escape is told apart from one that decodes`() {
        assertTrue(isBrokenUrl("/api/scopes?scope=%zz"))
        assertTrue(isBrokenUrl("/api/scope%zzaccess/orgs"))
        assertFalse(isBrokenUrl("/api/scopeaccess/orgs?scope=kartverk%3Amatrikkel.read"))
    }

    @Test
    fun `a request Ktor refuses for another reason gives 400 with INVALID_REQUEST, without blaming the URL`() =
        answerTo(BadRequestException("Can't transform call to resource")) { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("""{"error":"The request could not be read","code":"INVALID_REQUEST"}""", response.bodyAsText())
        }

    @Test
    fun `a failed call to Cloud KMS gives 502 with UPSTREAM_ERROR, and is logged with its stack trace`() =
        answerTo(CloudKmsException("Call to Cloud KMS failed: PERMISSION_DENIED", RuntimeException("PERMISSION_DENIED"))) { response ->
            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals("""{"error":"The call to Cloud KMS failed","code":"UPSTREAM_ERROR"}""", response.bodyAsText())
            val logged = appender.list.single { it.formattedMessage == "Call to Cloud KMS failed" }
            assertEquals(Level.ERROR, logged.level)
            assertEquals(CloudKmsException::class.java.name, logged.throwableProxy.className)
            assertTrue(logged.throwableProxy.stackTraceElementProxyArray.isNotEmpty())
        }

    @Test
    fun `a 5xx from Digdir is logged with its status and body`() =
        answerTo(DigdirErrorResponseException(503, "text/plain", "Service Unavailable".toByteArray())) { response ->
            assertEquals(HttpStatusCode.BadGateway, response.status)
            val logged = appender.list.single { it.formattedMessage == "Digdir answered HTTP 503: Service Unavailable" }
            assertEquals(Level.ERROR, logged.level)
        }

    @Test
    fun `an unexpected error gives 500 with its code, but not its message`() =
        answerTo(IllegalStateException("secret detail")) { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("""{"error":"Internal server error","code":"INTERNAL_ERROR"}""", response.bodyAsText())
        }
}
