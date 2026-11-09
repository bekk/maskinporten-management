package com.maskinporten

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DelegationsourcesTest {

    @Test
    fun testScopesGet() = testApp {
        client.get("/api/v1/delegationsources").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(jsonObjects().any { it.string("issuer") == "altinn" })
        }
    }
}
