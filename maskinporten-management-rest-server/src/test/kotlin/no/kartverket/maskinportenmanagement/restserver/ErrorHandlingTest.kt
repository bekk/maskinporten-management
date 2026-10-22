package no.kartverket.maskinportenmanagement.restserver

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
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

    private fun answerTo(request: suspend HttpClient.() -> HttpResponse, check: suspend (HttpResponse) -> Unit) = testApplication {
        application { module(testClient(), ExternalFiltering(null)) }
        check(client.request())
    }

    // Ktor's client cannot send a broken %-escape, so the check is tested on its own
    @Test
    fun `a URL with a broken escape is told apart from one that decodes`() {
        assertTrue(isBrokenUrl("/api/scopes?scope=%zz"))
        assertTrue(isBrokenUrl("/api/scope%zzaccess/orgs"))
        assertFalse(isBrokenUrl("/api/scopeaccess/orgs?scope=kartverk%3Amatrikkel.read"))
    }

    @Test
    fun `a request Ktor refuses for another reason gives 400 with malformed-request, without blaming the URL`() =
        answerTo(BadRequestException("Can't transform call to resource")) { response ->
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                """{"type":"tag:kartverket.no,2026:malformed-request","title":"Malformed request","status":400,""" +
                    """"detail":"The request could not be read"}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `a failed call to Cloud KMS gives 502 with upstream-error, and is logged with its stack trace`() =
        answerTo(CloudKmsException("Call to Cloud KMS failed: PERMISSION_DENIED", RuntimeException("PERMISSION_DENIED"))) { response ->
            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals(
                """{"type":"tag:kartverket.no,2026:upstream-error","title":"A service this API depends on failed",""" +
                    """"status":502,"detail":"The call to Cloud KMS failed"}""",
                response.bodyAsText(),
            )
            val logged = appender.list.single { it.formattedMessage == "Call to Cloud KMS failed" }
            assertEquals(Level.ERROR, logged.level)
            assertEquals(CloudKmsException::class.java.name, logged.throwableProxy.className)
            assertTrue(logged.throwableProxy.stackTraceElementProxyArray.isNotEmpty())
        }

    @Test
    fun `an unexpected error gives 500 with its type, but not its message`() =
        answerTo(IllegalStateException("secret detail")) { response ->
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(
                """{"type":"tag:kartverket.no,2026:internal-error","title":"Internal error","status":500}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `a path with no endpoint gives 404 with not-found`() = answerTo({ get("/api/scope") }) { response ->
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(
            """{"type":"tag:kartverket.no,2026:not-found","title":"Not found","status":404,""" +
                """"detail":"There is no endpoint at this path"}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `a method the endpoint does not accept gives 405 with method-not-allowed`() = answerTo({ patch("/api/scopes") }) { response ->
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals(
            """{"type":"tag:kartverket.no,2026:method-not-allowed","title":"Method not allowed","status":405,""" +
                """"detail":"The endpoint at this path does not accept this method"}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `every error is sent as application problem+json`() {
        for (failure in listOf(BadRequestException("Can't transform call to resource"), IllegalStateException("secret detail"))) {
            answerTo(failure) { response ->
                assertEquals(ContentType.Application.ProblemJson, response.contentType()?.withoutParameters(), "$failure")
            }
        }
        for (request in listOf<suspend HttpClient.() -> HttpResponse>({ get("/api/scope") }, { patch("/api/scopes") })) {
            answerTo(request) { response ->
                assertEquals(ContentType.Application.ProblemJson, response.contentType()?.withoutParameters(), "${response.status}")
            }
        }
    }
}
