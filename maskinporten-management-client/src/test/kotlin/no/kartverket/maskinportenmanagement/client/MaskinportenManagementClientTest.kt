package no.kartverket.maskinportenmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.maskinportenmanagement.client.auth.AccessToken
import no.kartverket.maskinportenmanagement.client.auth.AccessTokenProvider
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MaskinportenManagementClientTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun clientAnswering(
        baseUrl: URI = URI("https://digdir.test"),
        answer: (OutgoingRequest) -> OutgoingResponse = { OutgoingResponse(200, "application/json", "[]".toByteArray()) },
    ) = MaskinportenManagementClient(
        baseUrl,
        OutgoingHttpClient { request ->
            sent += request
            answer(request)
        },
        { AccessToken.of("test-token") },
    )

    @Test
    fun `lists scope access with only the scope in the query`() = runBlocking {
        clientAnswering().listScopeAccess(Scope.of("kartverk:matrikkel.read"))

        val request = sent.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("https://digdir.test/api/v1/scopes/access?scope=kartverk%3Amatrikkel.read", request.url.toString())
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("Bearer test-token", request.headers["Authorization"])
    }

    @Test
    fun `a scope cannot add query parameters of its own`() = runBlocking {
        clientAnswering().listScopeAccess(Scope.of("kartverk:x&consumer_orgno=123"))

        assertEquals("scope=kartverk%3Ax%26consumer_orgno%3D123", sent.single().url.rawQuery)
    }

    private val consumerOrgno = OrganizationNumber.of("311718371")

    @Test
    fun `lists a consumer's scope access with only the organisation in the query`() = runBlocking {
        clientAnswering().listConsumerScopeAccess(consumerOrgno)

        val request = sent.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("https://digdir.test/api/v1/scopes/access?consumer_orgno=311718371", request.url.toString())
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `removes scope access with the organisation in the path and only the scope in the query`() = runBlocking {
        clientAnswering().removeScopeAccess(consumerOrgno, Scope.of("kartverk:matrikkel.read"))

        val request = sent.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals(
            "https://digdir.test/api/v1/scopes/access/311718371?scope=kartverk%3Amatrikkel.read",
            request.url.toString(),
        )
    }

    @Test
    fun `grants scope access with the organisation in the path and only the scope in the query`() = runBlocking {
        clientAnswering().grantScopeAccess(consumerOrgno, Scope.of("kartverk:matrikkel.read"))

        val request = sent.single()
        assertEquals(HttpMethod.PUT, request.method)
        assertEquals(
            "https://digdir.test/api/v1/scopes/access/311718371?scope=kartverk%3Amatrikkel.read",
            request.url.toString(),
        )
    }

    @Test
    fun `a trailing slash on the base URL is ignored`() = runBlocking {
        clientAnswering(baseUrl = URI("https://digdir.test/")).listScopeAccess(Scope.of("kartverk:x"))

        assertEquals("/api/v1/scopes/access", sent.single().url.path)
    }

    @Test
    fun `returns Digdir's response as it is, whatever the status but 401`() = runBlocking {
        for (status in listOf(200, 400, 403, 404, 500)) {
            val response = OutgoingResponse(status, "application/json", """{"status":$status}""".toByteArray())

            assertSame(response, clientAnswering { response }.listScopeAccess(Scope.of("kartverk:x")), "for $status")
        }
    }

    @Test
    fun `a 401 from Digdir becomes a DigdirException, and the token is not used again`() {
        val refused = mutableListOf<AccessToken>()
        val client = MaskinportenManagementClient(
            URI("https://digdir.test"),
            { OutgoingResponse(401, "application/json", """{"error":"invalid_token"}""".toByteArray()) },
            object : AccessTokenProvider {
                override suspend fun accessToken() = AccessToken.of("test-token")
                override suspend fun refused(token: AccessToken) {
                    refused += token
                }
            },
        )

        val e = assertFailsWith<DigdirException> { runBlocking { client.listScopeAccess(Scope.of("kartverk:x")) } }

        assertContains(e.message!!, "401")
        assertEquals(listOf(AccessToken.of("test-token")), refused)
    }

    @Test
    fun `a call Digdir never answered becomes a DigdirException`() {
        val failure = IOException("Connection refused")

        val e = assertFailsWith<DigdirException> {
            runBlocking { clientAnswering { throw failure }.listScopeAccess(Scope.of("kartverk:x")) }
        }

        assertSame(failure, e.cause)
    }

    @Test
    fun `the DigdirException names the failure, even when it has no message`() {
        val e = assertFailsWith<DigdirException> {
            runBlocking { clientAnswering { throw ConnectException() }.listScopeAccess(Scope.of("kartverk:x")) }
        }

        assertContains(e.message!!, "ConnectException")
    }
}
