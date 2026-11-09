package no.kartverket.maskinportenmanagement.client

import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.HttpMethod.DELETE
import no.kartverket.maskinportenmanagement.client.http.HttpMethod.GET
import no.kartverket.maskinportenmanagement.client.http.HttpMethod.POST
import no.kartverket.maskinportenmanagement.client.http.HttpMethod.PUT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DigdirJsonTest {

    private val spec = DigdirSpec.load()

    private fun assertMatchesSpec(method: HttpMethod, path: String, body: String) {
        assertEquals(emptyList(), spec.mismatches(method, path, 200, body), "$method $path")
    }

    private val scope = digdirScope("kartverk:nrl.rapportering")
    private val access = digdirScopeAccess("kartverk:nrl.rapportering")

    @Test
    fun `a test scope matches every answer from Digdir that holds scopes`() {
        for (method in listOf(POST, PUT, DELETE)) assertMatchesSpec(method, "/api/v1/scopes", scope)
        assertMatchesSpec(GET, "/api/v1/scopes", jsonList(scope, digdirScope("kartverk:tilgangsstyring/demo.read")))
    }

    @Test
    fun `a test scope access matches every answer from Digdir that holds scope access`() {
        for (method in listOf(PUT, DELETE)) assertMatchesSpec(method, "/api/v1/scopes/access/{consumer_orgno}", access)
        assertMatchesSpec(GET, "/api/v1/scopes/access", jsonList(access, digdirScopeAccess("kartverk:nrl.rapportering", "910000000")))
    }

    @Test
    fun `a test changelog matches Digdir's answer with the changelog of scope access`() {
        assertMatchesSpec(GET, "/api/v1/scopes/access/changelog", digdirScopeAccessChangelog())
    }

    @Test
    fun `finds every field the spec does not have, however deep it is`() {
        val changelog = digdirScopeAccessChangelog().replace("\"id\":7", "\"id\":7,\"actor\":\"x\"").replace("\"size\"", "\"sizes\"")

        assertEquals(
            listOf("$.content[0].actor: not in the spec", "$.page.sizes: not in the spec"),
            spec.mismatches(GET, "/api/v1/scopes/access/changelog", 200, changelog),
        )
    }

    @Test
    fun `finds a value of the wrong type, outside the enum or in the wrong format`() {
        val wrong = scope.replace("\"active\":true", "\"active\":\"true\"")
            .replace("\"visibility\":\"PUBLIC\"", "\"visibility\":\"OPEN\"")
            .replace("\"created\":\"2026-01-15T09:30:00.123456Z\"", "\"created\":\"2026-01-15\"")
            .replace("\"at_max_age\":120", "\"at_max_age\":1.5")

        assertEquals(
            listOf(
                "$.active: expected boolean, got \"true\"",
                "$.created: \"2026-01-15\" is not a date-time",
                "$.visibility: \"OPEN\" is not one of [\"INTERNAL\",\"PRIVATE\",\"PUBLIC\"]",
                "$.at_max_age: expected integer, got 1.5",
            ),
            spec.mismatches(POST, "/api/v1/scopes", 200, wrong),
        )
    }

    @Test
    fun `finds a single object where Digdir answers with a list`() {
        assertEquals(listOf("$: expected array, got an object"), spec.mismatches(GET, "/api/v1/scopes", 200, scope))
    }

    @Test
    fun `refuses an answer the spec does not describe`() {
        assertFailsWith<IllegalArgumentException> { spec.mismatches(GET, "/api/v1/scopes/unknown", 200, "{}") }
        assertFailsWith<IllegalArgumentException> { spec.mismatches(PUT, "/api/v1/scopes", 201, scope) }
    }
}
