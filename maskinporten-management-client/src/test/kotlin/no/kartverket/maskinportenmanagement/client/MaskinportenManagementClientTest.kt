package no.kartverket.maskinportenmanagement.client

import kotlinx.coroutines.runBlocking
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
    )

    @Test
    fun `lists scope access with only the scope in the query`() = runBlocking {
        clientAnswering().listScopeAccess("kartverk:matrikkel.read")

        val request = sent.single()
        assertEquals("GET", request.method)
        assertEquals("https://digdir.test/api/v1/scopes/access?scope=kartverk%3Amatrikkel.read", request.url.toString())
        assertEquals("application/json", request.headers["Accept"])
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
    fun `an organisation number that is not 9 digits never reaches Digdir`() {
        for (consumerOrgno in listOf("..", ".", "", "31171837", "3117183710", "31171837a", "311718371/..", " 311718371")) {
            val e = assertFailsWith<IllegalArgumentException>(consumerOrgno) {
                runBlocking { clientAnswering().removeScopeAccess(consumerOrgno, "kartverk:matrikkel.read") }
            }

            assertContains(e.message!!, "9 digits", message = consumerOrgno)
        }
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a trailing slash on the base URL is ignored`() = runBlocking {
        clientAnswering(baseUrl = "https://digdir.test/").listScopeAccess("kartverk:x")

        assertEquals("/api/v1/scopes/access", sent.single().url.path)
    }

    @Test
    fun `returns Digdir's response as it is, whatever the status`() = runBlocking {
        for (status in listOf(200, 400, 403, 404, 500)) {
            val response = DigdirHttpResponse(status, "application/json", """{"status":$status}""".toByteArray())

            assertSame(response, clientAnswering { response }.listScopeAccess("kartverk:x"), "for $status")
        }
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
