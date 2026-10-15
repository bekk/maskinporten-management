package com.maskinporten

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationsTest {

    @Test
    fun testScopesGet() = testApp {
        client.get("/api/v1/integrations").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testOrgNumberFiltersOnCustomer() = testApp {
        val ids = client.get("/api/v1/integrations?org_number=958935420").jsonObjects().map { it.string("id") }
        assertEquals(setOf("planinnsyn-oidc", "byggesoknad-onbehalfof"), ids.toSet())

        val supplierOnly = client.get("/api/v1/integrations?org_number=971040238").jsonObjects()
        assertEquals(listOf("ansattportal-saml2"), supplierOnly.map { it.string("id") })
    }

    @Test
    fun testUpdatedSinceFiltersOnLastUpdated() = testApp {
        val integrations = client.get("/api/v1/integrations?updated_since=2026-03-23T00:00:00Z").jsonObjects()
        assertEquals(6, integrations.size)
        assertTrue(integrations.all { it.string("last_updated")!! >= "2026-03-23T00:00:00" })
    }

    @Test
    fun testTimestampsHaveNoTimeZone() = testApp {
        val integrations = client.get("/api/v1/integrations").jsonObjects()
        val format = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}""")
        assertTrue(integrations.all { format.matches(it.string("created")!!) && format.matches(it.string("last_updated")!!) })

        val matrikkel = integrations.first { it.string("id") == "matrikkel-oppslag" }
        assertEquals("2026-03-20T08:30:00", matrikkel.string("created"))
    }

    @Test
    fun testMissingOptionalFieldsAreLeftOut() = testApp {
        val integrations = client.get("/api/v1/integrations").jsonObjects().associateBy { it.string("id") }

        val withoutSupplier = integrations.getValue("skatt-eiendomsoppslag")
        assertTrue("supplier_organization_number" !in withoutSupplier)
        assertTrue("supplier_integration_id" !in withoutSupplier)

        assertTrue("description" !in integrations.getValue("digdir-api-klient"))
    }
}
