package com.maskinporten

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
