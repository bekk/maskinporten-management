package no.kartverket.maskinportenmanagement.client

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.auth.AccessTokenProvider
import no.kartverket.maskinportenmanagement.client.filtering.ScopeFilter
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpClient
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpRequest
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import java.io.IOException
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaskinportenManagementClientTest {

    private val sent = mutableListOf<DigdirHttpRequest>()

    private fun clientAnswering(
        baseUrl: String = "https://digdir.test",
        answer: (DigdirHttpRequest) -> DigdirHttpResponse = { DigdirHttpResponse(200, "application/json", "[]".toByteArray()) },
    ) = MaskinportenManagementClient(
        baseUrl,
        DigdirHttpClient { request ->
            sent += request
            answer(request)
        },
        { "test-token" },
    )

    private val scopes = """[
        {"name":"kartverk:tilgangsstyring/demo.read","prefix":"kartverk","active":true,"at_max_age":120},
        {"name":"kartverk:dokumentbestilling","prefix":"kartverk"},
        {"name":"kartverk:nrl.rapportering"},
        {"prefix":"kartverk","subscope":"uten.navn"},
        {"name":["kartverk:nrl.rapportering"]},
        "kartverk:nrl.rapportering"
    ]"""

    private val filter = ScopeFilter(exact = listOf("kartverk:nrl.rapportering"), prefix = listOf("kartverk:tilgangsstyring/"))

    @Test
    fun `lists Kartverket's scopes without a query`() = runBlocking {
        clientAnswering().listScopes()

        val request = sent.single()
        assertEquals("GET", request.method)
        assertEquals("https://digdir.test/api/v1/scopes", request.url.toString())
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `without a filter, returns Digdir's scopes as they are`() = runBlocking {
        val response = DigdirHttpResponse(200, "application/json", scopes.toByteArray())

        assertSame(response, clientAnswering { response }.listScopes())
    }

    @Test
    fun `with a filter, keeps only the scopes it allows, each one unchanged`() = runBlocking {
        val response = clientAnswering { DigdirHttpResponse(200, "application/json", scopes.toByteArray()) }.listScopes(filter)

        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.contentType)
        assertEquals(
            Json.parseToJsonElement(
                """[
                    {"name":"kartverk:tilgangsstyring/demo.read","prefix":"kartverk","active":true,"at_max_age":120},
                    {"name":"kartverk:nrl.rapportering"}
                ]""",
            ),
            Json.parseToJsonElement(response.body.decodeToString()),
        )
    }

    @Test
    fun `with a filter, passes Digdir's errors on as they are`() = runBlocking {
        for (status in listOf(400, 403, 500)) {
            val response = DigdirHttpResponse(status, "application/json", """{"status":$status}""".toByteArray())

            assertSame(response, clientAnswering { response }.listScopes(filter), "for $status")
        }
    }

    @Test
    fun `with a filter, an answer that is not a JSON list becomes a DigdirException`() {
        for (body in listOf("""{"name":"kartverk:nrl.rapportering"}""", "not json", "")) {
            val e = assertFailsWith<DigdirException>(body) {
                runBlocking { clientAnswering { DigdirHttpResponse(200, "application/json", body.toByteArray()) }.listScopes(filter) }
            }

            assertContains(e.message!!, "JSON list", message = body)
        }
    }

    @Test
    fun `lists scope access with only the scope in the query`() = runBlocking {
        clientAnswering().listScopeAccess("kartverk:matrikkel.read")

        val request = sent.single()
        assertEquals("GET", request.method)
        assertEquals("https://digdir.test/api/v1/scopes/access?scope=kartverk%3Amatrikkel.read", request.url.toString())
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("Bearer test-token", request.headers["Authorization"])
    }

    @Test
    fun `a scope cannot add query parameters of its own`() = runBlocking {
        clientAnswering().listScopeAccess("kartverk:x&consumer_orgno=123")

        assertEquals("scope=kartverk%3Ax%26consumer_orgno%3D123", sent.single().url.rawQuery)
    }

    @Test
    fun `removes scope access with the organisation in the path and only the scope in the query`() = runBlocking {
        clientAnswering().removeScopeAccess("311718371", "kartverk:matrikkel.read")

        val request = sent.single()
        assertEquals("DELETE", request.method)
        assertEquals(
            "https://digdir.test/api/v1/scopes/access/311718371?scope=kartverk%3Amatrikkel.read",
            request.url.toString(),
        )
    }

    @Test
    fun `grants scope access with the organisation in the path and only the scope in the query`() = runBlocking {
        clientAnswering().grantScopeAccess("311718371", "kartverk:matrikkel.read")

        val request = sent.single()
        assertEquals("PUT", request.method)
        assertEquals(
            "https://digdir.test/api/v1/scopes/access/311718371?scope=kartverk%3Amatrikkel.read",
            request.url.toString(),
        )
    }

    @Test
    fun `an organisation number that is not 9 digits never reaches Digdir`() {
        val calls = mapOf<String, suspend MaskinportenManagementClient.(String) -> Unit>(
            "grant" to { grantScopeAccess(it, "kartverk:matrikkel.read") },
            "remove" to { removeScopeAccess(it, "kartverk:matrikkel.read") },
        )
        for ((name, call) in calls) {
            for (consumerOrgno in listOf("..", ".", "", "31171837", "3117183710", "31171837a", "311718371/..", " 311718371")) {
                val e = assertFailsWith<IllegalArgumentException>("$name $consumerOrgno") {
                    runBlocking { clientAnswering().call(consumerOrgno) }
                }

                assertContains(e.message!!, "9 digits", message = "$name $consumerOrgno")
            }
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a trailing slash on the base URL is ignored`() = runBlocking {
        clientAnswering(baseUrl = "https://digdir.test/").listScopeAccess("kartverk:x")

        assertEquals("/api/v1/scopes/access", sent.single().url.path)
    }

    @Test
    fun `returns Digdir's response as it is, whatever the status but 401`() = runBlocking {
        for (status in listOf(200, 400, 403, 404, 500)) {
            val response = DigdirHttpResponse(status, "application/json", """{"status":$status}""".toByteArray())

            assertSame(response, clientAnswering { response }.listScopeAccess("kartverk:x"), "for $status")
        }
    }

    @Test
    fun `a 401 from Digdir becomes a DigdirException, and the token is not used again`() {
        val refused = mutableListOf<String>()
        val client = MaskinportenManagementClient(
            "https://digdir.test",
            { DigdirHttpResponse(401, "application/json", """{"error":"invalid_token"}""".toByteArray()) },
            object : AccessTokenProvider {
                override suspend fun accessToken() = "test-token"
                override suspend fun refused(token: String) {
                    refused += token
                }
            },
        )

        val e = assertFailsWith<DigdirException> { runBlocking { client.listScopeAccess("kartverk:x") } }

        assertContains(e.message!!, "401")
        assertEquals(listOf("test-token"), refused)
    }

    @Test
    fun `a call Digdir never answered becomes a DigdirException`() {
        val failure = IOException("Connection refused")

        val e = assertFailsWith<DigdirException> {
            runBlocking { clientAnswering { throw failure }.listScopeAccess("kartverk:x") }
        }

        assertSame(failure, e.cause)
    }

    @Test
    fun `the DigdirException names the failure, even when it has no message`() {
        val e = assertFailsWith<DigdirException> {
            runBlocking { clientAnswering { throw ConnectException() }.listScopeAccess("kartverk:x") }
        }

        assertContains(e.message!!, "ConnectException")
    }
}
