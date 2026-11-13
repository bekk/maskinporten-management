package no.kartverket.maskinportenmanagement.restserver.scopes

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.kartverket.maskinportenmanagement.client.digdirScope
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.json
import no.kartverket.maskinportenmanagement.client.http.recording
import no.kartverket.maskinportenmanagement.restserver.errorResponse
import no.kartverket.maskinportenmanagement.restserver.malformedRequest
import no.kartverket.maskinportenmanagement.restserver.problem.FieldError
import no.kartverket.maskinportenmanagement.restserver.problem.ProblemType
import no.kartverket.maskinportenmanagement.restserver.problem.ValidationCode
import no.kartverket.maskinportenmanagement.restserver.testServer
import no.kartverket.maskinportenmanagement.restserver.validationFailed
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateScopeTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun createScopeTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testServer(recording(sent) { json(200, digdirScope("kartverk:tilgangsstyring/demo.read")) }, filtering = null, block)

    private suspend fun ApplicationTestBuilder.createScope(body: ByteArray, contentType: ContentType? = ContentType.Application.Json) =
        client.post("/api/scopes") {
            if (contentType != null) contentType(contentType)
            setBody(body)
        }

    @Test
    fun `sends the scope to Digdir byte for byte`() = createScopeTest {
        val body = """{ "prefix": "kartverk",
            "subscope": "tilgangsstyring/demo.read", "description": "Lesetilgang, æøå" }""".toByteArray(Charsets.UTF_8)

        assertEquals(HttpStatusCode.OK, createScope(body).status)

        val request = sent.single()
        assertEquals("POST", request.method.name)
        assertEquals("https://digdir.test/api/v1/scopes", request.url.toString())
        assertEquals("application/json", request.headers["Content-Type"])
        assertContentEquals(body, request.body)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesThatAreNotAJsonObject")
    fun `a body that is not a JSON object is refused without calling Digdir`(body: String, detail: String) = createScopeTest {
        val response = createScope(body.toByteArray())

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val answer = response.errorResponse()
        assertEquals(ProblemType.MALFORMED_REQUEST, answer.type)
        assertTrue(answer.detail.orEmpty().startsWith(detail), answer.detail)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a query and a wrong body are both listed, without calling Digdir`() = createScopeTest {
        val response = client.post("/api/scopes?subscope=matrikkel.read") {
            contentType(ContentType.Application.Json)
            setBody("""{"prefix":"kartverk"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            validationFailed(
                FieldError("subscope", ValidationCode.NOT_ALLOWED, "subscope is not allowed"),
                FieldError("subscope", ValidationCode.MISSING, "subscope is required"),
            ),
            response.errorResponse(),
        )
        assertTrue(sent.isEmpty())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesThatAreNotUtf8Json")
    fun `a body that is not UTF-8 JSON is refused without calling Digdir`(
        case: String,
        body: ByteArray,
        contentType: ContentType?,
        error: String,
    ) = createScopeTest {
        val response = createScope(body, contentType)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(malformedRequest(error), response.errorResponse())
        assertTrue(sent.isEmpty())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadableContentTypes")
    fun `a Content-Type that cannot be read is refused without calling Digdir`(contentType: ContentType) = createScopeTest {
        val response = client.post("/api/scopes") {
            setBody(ByteArrayContent("""{"prefix":"kartverk"}""".toByteArray(), contentType))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            malformedRequest("The body must be JSON, sent with Content-Type application/json"),
            response.errorResponse(),
        )
        assertTrue(sent.isEmpty())
    }

    companion object {
        @JvmStatic
        fun bodiesThatAreNotAJsonObject(): List<Arguments> = listOf(
            Arguments.of("[]", "Expected a scope as a JSON object"),
            Arguments.of("""{"prefix":"kartverk","active":True}""", "The body is not valid JSON: "),
            Arguments.of("""{"subscope":"a","subscope":"b"}""", "The body is not valid JSON: "),
        )

        @JvmStatic
        fun bodiesThatAreNotUtf8Json(): List<Arguments> {
            val scope = """{"prefix":"kartverk"}""".toByteArray()
            return listOf(
                Arguments.of("no Content-Type", scope, null, "The body must be JSON, sent with Content-Type application/json"),
                Arguments.of("text/plain", scope, ContentType.Text.Plain, "The body must be JSON, sent with Content-Type application/json"),
                Arguments.of("bytes that are not UTF-8", byteArrayOf(0x7B, 0xFF.toByte(), 0x7D), ContentType.Application.Json, "The body must be UTF-8"),
            )
        }

        @JvmStatic
        fun unreadableContentTypes(): List<ContentType> = listOf(ContentType("application", ""), ContentType("", "json"))
    }
}
