package no.kartverket.maskinportenmanagement.restserver

import kotlinx.coroutines.runBlocking
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.auth.AccessToken
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFilteringClient
import no.kartverket.maskinportenmanagement.restserver.filtering.ExternalFilteringException
import no.kartverket.maskinportenmanagement.restserver.filtering.ScopeFilter
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeFilteringServiceTest {

    private val filtering = ExternalFilteringClient(URI("https://filtering.test/scopes"), fakeFilteringService)

    private fun certificate(namespace: String, app: String) =
        "By=spiffe://cluster.local/ns/tilgangsstyring/sa/maskinporten-management;Hash=4f1c2b;Subject=\"\";" +
            "URI=spiffe://cluster.local/ns/$namespace/sa/$app"

    @Test
    fun `a known app gets its exact scopes and prefixes`() = runBlocking {
        assertEquals(
            ScopeFilter(exact = emptyList(), prefix = listOf("kartverk:tilgangsstyring/")),
            filtering.filterFor(certificate("tilgangsstyring", "demo")),
        )
        assertEquals(
            ScopeFilter(
                exact = listOf("kartverk:matrikkel.read", "kartverk:matrikkel.write", "kartverk:adresse.read"),
                prefix = emptyList(),
            ),
            filtering.filterFor(certificate("matrikkel", "matrikkel-api")),
        )
    }

    @Test
    fun `an unknown app gets empty lists`() = runBlocking {
        assertEquals(
            ScopeFilter(exact = emptyList(), prefix = emptyList()),
            filtering.filterFor(certificate("tilgangsstyring", "unknown")),
        )
    }

    @Test
    fun `refuses a certificate without a SPIFFE URI`() {
        for (cert in listOf("", "By=spiffe://cluster.local/ns/tilgangsstyring/sa/demo")) {
            assertFailsWith<ExternalFilteringException>(cert) { runBlocking { filtering.filterFor(cert) } }
        }
    }

    @Test
    fun `the app is the last SPIFFE URI, like OPA, not one the caller sent itself`() = runBlocking {
        val cert = "URI=spiffe://cluster.local/ns/matrikkel/sa/matrikkel-api," + certificate("tilgangsstyring", "demo")

        assertEquals(listOf("kartverk:tilgangsstyring/"), filtering.filterFor(cert).prefix)
    }

    @Test
    fun `the test apps only reference scopes the fake Digdir seeds`() {
        testApps.forEach { (app, filter) ->
            filter.exact.forEach { scope ->
                assertContains(seededScopes, scope, "$app references $scope, which the fake Digdir does not seed")
            }
            filter.prefix.forEach { prefix ->
                assertTrue(
                    seededScopes.any { it.startsWith(prefix) },
                    "$app has prefix $prefix that no seeded scope starts with",
                )
            }
        }
    }

    private val seededScopes: Set<String> = runBlocking {
        MaskinportenManagementClient(URI("http://digdir.local"), FakeDigdir()) { AccessToken.of("local") }
            .listScopes().map { it.name.value }.toSet()
    }
}
