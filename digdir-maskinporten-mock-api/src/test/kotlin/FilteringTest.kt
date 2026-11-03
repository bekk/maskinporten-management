package com.maskinporten

import com.maskinporten.filtering.CLIENT_CERTIFICATE_HEADER
import com.maskinporten.filtering.testApps
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilteringTest {

    private fun clientCertificate(namespace: String, app: String) =
        "By=spiffe://cluster.local/ns/tilgangsstyring/sa/maskinporten-management;Hash=4f1c2b;Subject=\"\";" +
            "URI=spiffe://cluster.local/ns/$namespace/sa/$app"

    private suspend fun ApplicationTestBuilder.filterFor(clientCertificate: String?): HttpResponse =
        client.post("/filtering") {
            contentType(ContentType.Application.Json)
            val headers = clientCertificate?.let { """{"x-forwarded-client-cert":${Json.encodeToString(String.serializer(), it)}}""" } ?: "{}"
            setBody("""{"input":{"headers":$headers}}""")
        }

    private suspend fun HttpResponse.result(): JsonObject = jsonObject().getValue("result").jsonObject

    @Test
    fun `a test app gets its exact scopes and prefixes, as the result`() = testApp {
        filterFor(clientCertificate("tilgangsstyring", "demo")).apply {
            assertEquals(HttpStatusCode.OK, status)
            val filter = result()
            assertEquals(emptyList(), filter.strings("exact"))
            assertEquals(listOf("kartverk:tilgangsstyring/"), filter.strings("prefix"))
        }
    }

    @Test
    fun `an unknown app gets empty lists`() = testApp {
        filterFor(clientCertificate("tilgangsstyring", "unknown")).apply {
            assertEquals(HttpStatusCode.OK, status)
            val filter = result()
            assertEquals(emptyList(), filter.strings("exact"))
            assertEquals(emptyList(), filter.strings("prefix"))
        }
    }

    @Test
    fun `refuses a call without a SPIFFE URI in the input`() = testApp {
        assertEquals(HttpStatusCode.BadRequest, filterFor(null).status)
        assertEquals(HttpStatusCode.BadRequest, filterFor("By=spiffe://cluster.local/ns/tilgangsstyring/sa/demo").status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/filtering") { header(CLIENT_CERTIFICATE_HEADER, clientCertificate("tilgangsstyring", "demo")) }.status)
    }

    @Test
    fun `the app is the last SPIFFE URI, like OPA, not one the caller sent itself`() = testApp {
        val certificate = "URI=spiffe://cluster.local/ns/matrikkel/sa/matrikkel-api," + clientCertificate("tilgangsstyring", "demo")

        assertEquals(listOf("kartverk:tilgangsstyring/"), filterFor(certificate).result().strings("prefix"))
    }

    @Test
    fun `the test apps fit the scopes in the mock`() = testApp {
        val scopes = client.get("/api/v1/scopes").jsonObjects().mapNotNull { it.string("name") }

        testApps.forEach { (app, filter) ->
            assertTrue(filter.exact.all { it in scopes }, "$app has an exact scope the mock does not have")
            assertTrue(filter.prefix.all { prefix -> scopes.any { it.startsWith(prefix) } }, "$app has a prefix no scope starts with")
        }
    }
}
