package com.maskinporten

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApisTest {

    @Test
    fun testApisGet() = testApp {
        client.get("/api/v1/apis").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}