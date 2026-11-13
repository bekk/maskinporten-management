package no.kartverket.maskinportenmanagement.restserver

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.digdirScopeAccess
import no.kartverket.maskinportenmanagement.client.digdirScopeAccessChangelog
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.jsonList
import no.kartverket.maskinportenmanagement.restserver.problem.ErrorResponse
import no.kartverket.maskinportenmanagement.restserver.problem.ProblemType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DigdirAnswersTest {

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

    class Endpoint(val method: HttpMethod, val pathAndQuery: String, val digdirSuccess: String, val body: String? = null) {
        override fun toString() = "${method.value} $pathAndQuery"
    }

    private fun digdirAnswering(answer: (OutgoingRequest) -> OutgoingResponse, block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(
            digdir = OutgoingHttpClient { answer(it) },
            filtering = OutgoingHttpClient { json(200, """{"result":{"exact":[],"prefix":["kartverk:"]}}""") },
            block = block,
        )

    private suspend fun ApplicationTestBuilder.call(endpoint: Endpoint): HttpResponse =
        client.request(endpoint.pathAndQuery) {
            method = endpoint.method
            header("X-Forwarded-Client-Cert", CLIENT_CERTIFICATE)
            if (endpoint.body != null) {
                contentType(ContentType.Application.Json)
                setBody(endpoint.body)
            }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    fun `answers with Digdir's data in this API's model`(endpoint: Endpoint) =
        digdirAnswering({ json(200, endpoint.digdirSuccess) }) {
            val response = call(endpoint)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals(Json.parseToJsonElement(endpoint.digdirSuccess), Json.parseToJsonElement(response.bodyAsText()))
        }

    @ParameterizedTest(name = "{0}, Digdir answers {1}")
    @MethodSource("endpointsAndRejections")
    fun `a rejection from Digdir keeps Digdir's status, with Digdir's explanation as upstream-rejected`(endpoint: Endpoint, status: Int) =
        digdirAnswering({ json(status, """{"status":409,"error":"Conflict","error_description":"finnes allerede: æøå"}""") }) {
            val response = call(endpoint)

            assertEquals(status, response.status.value)
            assertEquals(ContentType.Application.ProblemJson, response.contentType()?.withoutParameters())
            assertEquals(
                ErrorResponse.of(ProblemType.UPSTREAM_REJECTED, HttpStatusCode.fromValue(status), "finnes allerede: æøå"),
                response.errorResponse(),
            )
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("explainedRejections")
    fun `explains a rejection with Digdir's explanation, else only that Digdir rejected it`(
        case: String,
        answer: OutgoingResponse,
        detail: String,
    ) = digdirAnswering({ answer }) {
        val response = call(endpoints.first())

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ErrorResponse.of(ProblemType.UPSTREAM_REJECTED, HttpStatusCode.NotFound, detail), response.errorResponse())
    }

    @ParameterizedTest(name = "{0}, Digdir answers {1}")
    @MethodSource("endpointsAndFailures")
    fun `any other error status from Digdir gives 502, since the caller did not cause it`(endpoint: Endpoint, status: Int) =
        digdirAnswering({ OutgoingResponse(status, "text", "Service Unavailable".toByteArray()) }) {
            assertDigdirFailed(call(endpoint))
        }

    @Test
    fun `a rejection from Digdir is logged with Digdir's whole answer, in one line`() {
        val answer = """{"error_description":"finnes allerede","correlation_id":"abc-123"}"""
        digdirAnswering({ json(409, answer) }) {
            val response = call(endpoints.first())

            assertEquals(HttpStatusCode.Conflict, response.status)
            val logged = appender.list.single { it.formattedMessage.startsWith("Digdir rejected") }
            assertEquals(Level.INFO, logged.level)
            assertEquals("Digdir rejected the request with HTTP 409: $answer", logged.formattedMessage)
            assertNull(logged.throwableProxy)
        }
    }

    @Test
    fun `a failure at Digdir is logged as an error with Digdir's whole answer`() =
        digdirAnswering({ OutgoingResponse(503, "text/plain", "Service Unavailable".toByteArray()) }) {
            val response = call(endpoints.first())

            assertEquals(HttpStatusCode.BadGateway, response.status)
            val logged = appender.list.single { it.formattedMessage.startsWith("Digdir answered") }
            assertEquals(Level.ERROR, logged.level)
            assertEquals("Digdir answered HTTP 503: Service Unavailable", logged.formattedMessage)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    fun `a 401 from Digdir gives 502, since it means our token is wrong, not the caller`(endpoint: Endpoint) =
        digdirAnswering({ json(401, """{"error":"invalid_token"}""") }) {
            assertDigdirFailed(call(endpoint))
        }

    @ParameterizedTest(name = "{0}, Digdir answers {1}")
    @MethodSource("endpointsAndUnreadableAnswers")
    fun `gives 502 when Digdir answers with something this API cannot read`(endpoint: Endpoint, case: String, answer: OutgoingResponse) =
        digdirAnswering({ answer }) {
            assertDigdirFailed(call(endpoint))
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    fun `gives 502 when Digdir cannot be reached`(endpoint: Endpoint) =
        digdirAnswering({ throw IOException("Connection refused") }) {
            assertDigdirFailed(call(endpoint))
        }

    private suspend fun assertDigdirFailed(response: HttpResponse) {
        assertEquals(HttpStatusCode.BadGateway, response.status)
        assertEquals(upstreamError("The call to Digdir failed"), response.errorResponse())
    }

    companion object {
        private val access = digdirScopeAccess("kartverk:matrikkel.read")

        private val endpoints = listOf(
            Endpoint(HttpMethod.Get, "/api/scopes", jsonList(digdirScope("kartverk:matrikkel.read"))),
            Endpoint(
                HttpMethod.Post,
                "/api/scopes",
                digdirScope("kartverk:matrikkel.read"),
                body = """{"prefix":"kartverk","subscope":"matrikkel.read"}""",
            ),
            Endpoint(
                HttpMethod.Put,
                "/api/scopes?scope=kartverk:matrikkel.read",
                digdirScope("kartverk:matrikkel.read"),
                body = """{"prefix":"kartverk","subscope":"matrikkel.read"}""",
            ),
            Endpoint(HttpMethod.Delete, "/api/scopes?scope=kartverk:matrikkel.read", digdirScope("kartverk:matrikkel.read")),
            Endpoint(HttpMethod.Get, "/api/scopeaccess/orgs?scope=kartverk:matrikkel.read", jsonList(access)),
            Endpoint(HttpMethod.Get, "/api/scopeaccess/scopes?orgnr=311718371", jsonList(access)),
            Endpoint(HttpMethod.Get, "/api/scopeaccess/changelog?scope=kartverk:matrikkel.read", digdirScopeAccessChangelog()),
            Endpoint(HttpMethod.Put, "/api/scopeaccess/311718371?scope=kartverk:matrikkel.read", access),
            Endpoint(HttpMethod.Delete, "/api/scopeaccess/311718371?scope=kartverk:matrikkel.read", access),
        )

        @JvmStatic
        fun endpoints(): List<Endpoint> = endpoints

        @JvmStatic
        fun endpointsAndRejections(): List<Arguments> =
            endpoints.flatMap { endpoint -> listOf(400, 403, 404, 409).map { Arguments.of(endpoint, it) } }

        @JvmStatic
        fun endpointsAndFailures(): List<Arguments> =
            endpoints.flatMap { endpoint -> listOf(302, 500, 503).map { Arguments.of(endpoint, it) } }

        @JvmStatic
        fun endpointsAndUnreadableAnswers(): List<Arguments> = endpoints.flatMap { endpoint ->
            listOf(
                Arguments.of(endpoint, "an object this API does not know", json(200, """{"unexpected":true}""")),
                Arguments.of(endpoint, "204 with no body", OutgoingResponse(204, null, ByteArray(0))),
            )
        }

        @JvmStatic
        fun explainedRejections(): List<Arguments> = listOf(
            Arguments.of(
                "error_description",
                json(404, """{"error":"not_found","error_description":"Scope finnes ikke"}"""),
                "Scope finnes ikke",
            ),
            Arguments.of("an HTML page", OutgoingResponse(404, "text/html", "<h1>Not Found</h1>".toByteArray()), "Digdir rejected the request"),
        )
    }
}
