package no.kartverket.maskinportenmanagement.client

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import no.kartverket.maskinportenmanagement.client.exception.MaskinportenApiException
import no.kartverket.maskinportenmanagement.client.http.ManagementHttpClient
import no.kartverket.maskinportenmanagement.client.support.TestHttpServer
import no.kartverket.maskinportenmanagement.client.support.TestResponse
import no.kartverket.maskinportenmanagement.client.support.testHttpClient
import java.io.IOException
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaskinportenManagementClientTest {

    private lateinit var server: TestHttpServer

    @BeforeTest
    fun startServer() {
        server = TestHttpServer.start()
    }

    @AfterTest
    fun stopServer() = server.close()

    private fun client(baseUrl: String = server.baseUrl + API_PATH) =
        MaskinportenManagementClient(baseUrl, testHttpClient)

    private fun answer(response: TestResponse) {
        server.on(API_PATH + MaskinportenManagementClient.SCOPE_ACCESS_PATH) { response }
    }

    private val lastRequest get() = server.lastRequest(API_PATH + MaskinportenManagementClient.SCOPE_ACCESS_PATH)

    @Test
    fun `asks for the access to the given scope, url encoded`() = runBlocking {
        answer(TestResponse(body = "[]"))

        client().getScopeAccess(ScopeName.parse("kartverk:matrikkel.read"))

        assertEquals("GET", lastRequest.method)
        assertEquals("scope=kartverk%3Amatrikkel.read", lastRequest.query)
        assertEquals("application/json", lastRequest.header("Accept"))
    }

    @Test
    fun `a trailing slash on the base url does not double the slash`() = runBlocking {
        answer(TestResponse(body = "[]"))

        client(server.baseUrl + API_PATH + "/").getScopeAccess(ScopeName.parse("kartverk:matrikkel.read"))

        assertEquals(1, server.requestCount(API_PATH + MaskinportenManagementClient.SCOPE_ACCESS_PATH))
    }

    @Test
    fun `parses every consumer in the response`() = runBlocking {
        answer(TestResponse(body = "[${accessJson()},${accessJson(consumerOrgno = "958935420", state = "REQUESTED")}]"))

        val access = client().getScopeAccess(ScopeName.parse("kartverk:matrikkel.read"))

        assertEquals(
            listOf(
                ScopeAccess(
                    scope = "kartverk:matrikkel.read",
                    ownerOrgno = "971040238",
                    ownerOrganizationName = "Statens kartverk",
                    consumerOrgno = "971032081",
                    consumerOrganizationName = "Statens vegvesen",
                    state = ScopeAccessState.APPROVED,
                    created = Instant.parse("2026-03-01T08:00:00Z"),
                    lastUpdated = Instant.parse("2026-03-02T08:00:00Z"),
                ),
                ScopeAccess(
                    scope = "kartverk:matrikkel.read",
                    ownerOrgno = "971040238",
                    ownerOrganizationName = "Statens kartverk",
                    consumerOrgno = "958935420",
                    consumerOrganizationName = "Statens vegvesen",
                    state = ScopeAccessState.REQUESTED,
                    created = Instant.parse("2026-03-01T08:00:00Z"),
                    lastUpdated = Instant.parse("2026-03-02T08:00:00Z"),
                ),
            ),
            access,
        )
    }

    @Test
    fun `organization names are optional and unknown fields are ignored`() = runBlocking {
        answer(
            TestResponse(
                body = """[{"scope":"kartverk:matrikkel.read","owner_orgno":"971040238","consumer_orgno":"971032081",""" +
                    """"state":"DENIED","active":true,"created":"2026-03-01T08:00:00Z",""" +
                    """"last_updated":"2026-03-01T08:00:00Z"}]""",
            ),
        )

        val access = client().getScopeAccess(ScopeName.parse("kartverk:matrikkel.read")).single()

        assertNull(access.ownerOrganizationName)
        assertNull(access.consumerOrganizationName)
        assertEquals(ScopeAccessState.DENIED, access.state)
    }

    @Test
    fun `a scope nobody has access to gives an empty list`() = runBlocking {
        answer(TestResponse(body = "[]"))

        assertEquals(emptyList(), client().getScopeAccess(ScopeName.parse("kartverk:ingen")))
    }

    @Test
    fun `a status other than 200 throws with the status and body`() {
        answer(TestResponse(status = 500, body = """{"error":"boom"}"""))

        val e = assertFailsWith<MaskinportenApiException> {
            runBlocking { client().getScopeAccess(ScopeName.parse("kartverk:matrikkel.read")) }
        }

        assertEquals(500, e.statusCode)
        assertEquals("""{"error":"boom"}""", e.responseBody)
        assertContains(e.message!!, "Maskinporten API responded 500")
    }

    @Test
    fun `a response that does not parse throws`() {
        for (body in listOf("not json", "[${accessJson(state = "PENDING")}]", "[${accessJson(created = "yesterday")}]")) {
            answer(TestResponse(body = body))

            val e = assertFailsWith<MaskinportenApiException>(body) {
                runBlocking { client().getScopeAccess(ScopeName.parse("kartverk:matrikkel.read")) }
            }

            assertContains(e.message!!, "Failed to parse the scope access response", message = body)
            assertIs<SerializationException>(e.cause, body)
        }
    }

    @Test
    fun `a failed connection throws, keeping the cause`() {
        val failing = ManagementHttpClient { throw IOException("connection refused") }

        val e = assertFailsWith<MaskinportenApiException> {
            runBlocking {
                MaskinportenManagementClient("http://localhost", failing)
                    .getScopeAccess(ScopeName.parse("kartverk:matrikkel.read"))
            }
        }

        assertNull(e.statusCode)
        assertTrue(e.cause is IOException)
    }

    private companion object {
        const val API_PATH = "/api/v1"

        fun accessJson(
            consumerOrgno: String = "971032081",
            state: String = "APPROVED",
            created: String = "2026-03-01T08:00:00Z",
        ): String =
            """{"scope":"kartverk:matrikkel.read","owner_orgno":"971040238",""" +
                """"owner_organization_name":"Statens kartverk","consumer_orgno":"$consumerOrgno",""" +
                """"consumer_organization_name":"Statens vegvesen","state":"$state","created":"$created",""" +
                """"last_updated":"2026-03-02T08:00:00Z"}"""
    }
}
