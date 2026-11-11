package no.kartverket.maskinportenmanagement.restserver

import kotlinx.coroutines.runBlocking
import no.kartverket.maskinportenmanagement.client.DigdirErrorResponseException
import no.kartverket.maskinportenmanagement.client.MaskinportenManagementClient
import no.kartverket.maskinportenmanagement.client.OrganizationNumber
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.ScopeAccessState
import no.kartverket.maskinportenmanagement.client.auth.AccessToken
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeDigdirTest {

    private val digdir = MaskinportenManagementClient(URI("http://digdir.local"), FakeDigdir()) { AccessToken.of("local") }

    private val newScope = Scope.of("kartverk:lokal/test.read")
    private val consumer = OrganizationNumber.of("958935420")

    @Test
    fun `starts with Kartverket's seeded scopes and who has access to them`() = runBlocking {
        assertTrue(Scope.of("kartverk:matrikkel.read") in digdir.listScopes().map { it.name })
        assertEquals(
            listOf("971032081", "942110464"),
            digdir.listScopeAccess(Scope.of("kartverk:adresse.read")).map { it.consumerOrgno.value },
        )
    }

    @Test
    fun `creates, updates and deletes a scope, and keeps a deleted one only as inactive`() = runBlocking {
        val created = digdir.createScope("""{"prefix":"kartverk","subscope":"lokal/test.read","description":"Test"}""")
        assertEquals(newScope, created.name)
        assertTrue(created.active)

        val updated = digdir.updateScope(newScope, """{"description":"Endret","prefix":"other"}""")
        assertEquals("Endret", updated.description)
        assertEquals("kartverk", updated.prefix)

        assertFalse(digdir.deleteScope(newScope).active)
        assertFalse(newScope in digdir.listScopes().map { it.name })
        assertEquals(404, assertFailsWith<DigdirErrorResponseException> { digdir.deleteScope(newScope) }.statusCode)
    }

    @Test
    fun `refuses a scope that already exists`() {
        val scope = """{"prefix":"kartverk","subscope":"matrikkel.read"}"""

        assertEquals(409, assertFailsWith<DigdirErrorResponseException> { runBlocking { digdir.createScope(scope) } }.statusCode)
    }

    @Test
    fun `refuses what Digdir refuses, and keeps answering for the scopes it has`() {
        val scope = Scope.of("kartverk:matrikkel.read")
        val refused = listOf<suspend () -> Unit>(
            { digdir.createScope("""{"prefix":"kartverk","subscope":"med mellomrom"}""") },
            { digdir.updateScope(scope, """{"active":"ja"}""") },
            { digdir.updateScope(scope, """{"token_type":"invalid"}""") },
            { digdir.updateScope(scope, """{"visibility":"invalid"}""") },
            { digdir.updateScope(scope, """{"allowed_integration_types":["invalid"]}""") },
            { digdir.updateScope(scope, "{") },
        )

        for (call in refused) {
            assertEquals(400, assertFailsWith<DigdirErrorResponseException> { runBlocking { call() } }.statusCode)
        }
        val otherPrefix = assertFailsWith<DigdirErrorResponseException> {
            runBlocking { digdir.createScope("""{"prefix":"nav","subscope":"test"}""") }
        }
        assertEquals(403, otherPrefix.statusCode)
        assertEquals(6, runBlocking { digdir.listScopes() }.size)
    }

    @Test
    fun `gives and removes access, and logs both in the changelog`() = runBlocking {
        val scope = Scope.of("kartverk:matrikkel.read")

        assertEquals(ScopeAccessState.APPROVED, digdir.grantScopeAccess(consumer, scope).state)
        assertTrue(scope in digdir.listConsumerScopeAccess(consumer).map { it.scope })

        val removed = digdir.removeScopeAccess(consumer, scope)
        assertFalse(scope in digdir.listConsumerScopeAccess(consumer).map { it.scope })

        assertTrue(digdir.grantScopeAccess(consumer, scope).lastUpdated >= removed.lastUpdated)
        assertEquals(
            listOf("Scope access created", "Scope access deleted", "Scope access created"),
            digdir.getScopeAccessChangelog(scope).content.map { it.message },
        )
    }

    @Test
    fun `answers 404 for a scope it does not have`() {
        val e = assertFailsWith<DigdirErrorResponseException> {
            runBlocking { digdir.grantScopeAccess(consumer, Scope.of("kartverk:finnes.ikke")) }
        }

        assertEquals(404, e.statusCode)
    }
}
