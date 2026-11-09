package com.maskinporten

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrefixTest {

    @Test
    fun testPrefixGet() = testApp {
        client.get("/api/v1/prefix").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testAllPrefixGet() = testApp {
        client.get("/api/v1/prefix/all").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testInactivePrefixesAreOnlyReturnedWhenRequested() = testApp {
        val prefixes = client.get("/api/v1/prefix/all").jsonObjects().map { it.string("prefix") }
        assertTrue("kartverk-legacy" !in prefixes)

        val withInactive = client.get("/api/v1/prefix/all?inactive=true").jsonObjects().map { it.string("prefix") }
        assertTrue("kartverk-legacy" in withInactive)
    }

    @Test
    fun testMissingOrganizationNameIsLeftOut() = testApp {
        val prefix = client.get("/api/v1/prefix/all").jsonObjects().first { it.string("prefix") == "skatt" }
        assertTrue("organization_name" !in prefix)
    }
}
