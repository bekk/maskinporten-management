package com.maskinporten

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun testRoot() = testApp {
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun swaggerUiServesApiContract() = testApp {
        client.get("/swagger").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("maskinporten-api.json"))
        }
        client.get("/swagger/maskinporten-api.json").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("\"openapi\""))
        }
    }

}
