package com.maskinporten

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopesAccessTest {

    @Test
    fun testScopeAccessGet() = testApp {
        client.get("/api/v1/scopes/access?consumer_orgno=971032081&scope=kartverk:matrikkel.read").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testScopeAccessPut() = testApp {
        client.put("/api/v1/scopes/access/971032081?scope=kartverk:matrikkel.read").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testScopeAccessDelete() = testApp {
        client.put("/api/v1/scopes/access/958935420?scope=kartverk:sefrak.read").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.delete("/api/v1/scopes/access/958935420?scope=kartverk:sefrak.read").apply {
            assertEquals(HttpStatusCode.OK, status)
            val access = jsonObject()
            assertEquals("kartverk:sefrak.read", access.string("scope"))
            assertEquals("958935420", access.string("consumer_orgno"))
            assertEquals("APPROVED", access.string("state"))
        }
    }

    @Test
    fun testScopeAccessGetWithoutScope() = testApp {
        client.get("/api/v1/scopes/access?consumer_orgno=971032081").apply {
            assertEquals(HttpStatusCode.OK, status)
            val access = jsonObjects()
            assertTrue(access.size > 1)
            assertTrue(access.all { it.string("consumer_orgno") == "971032081" })
        }
    }

    @Test
    fun testReapprovingInactiveAccessMakesItActive() = testApp {
        val path = "/api/v1/scopes/access?consumer_orgno=991825827&scope=kartverk:grunnbok.read"
        assertTrue(client.get(path).jsonObjects().isEmpty())
        assertEquals(1, client.get("$path&inactive=true").jsonObjects().size)

        client.put("/api/v1/scopes/access/991825827?scope=kartverk:grunnbok.read").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        assertEquals(1, client.get(path).jsonObjects().size)
    }

    @Test
    fun testInactiveScopeAccessIsOnlyReturnedWhenRequested() = testApp {
        val isInactiveSeed: (JsonObject) -> Boolean = {
            it.string("scope") == "kartverk:grunnbok.read" && it.string("consumer_orgno") == "991825827"
        }

        val active = client.get("/api/v1/scopes/access/all").jsonObjects()
        assertTrue(active.none(isInactiveSeed))

        val all = client.get("/api/v1/scopes/access/all?inactive=true").jsonObjects()
        assertTrue(all.any(isInactiveSeed))
        assertTrue(all.none { "active" in it })
    }

    @Test
    fun testNewAccessGetsOrganizationNames() = testApp {
        client.put("/api/v1/scopes/access/958935420?scope=kartverk:adresse.read").apply {
            assertEquals(HttpStatusCode.OK, status)
            val access = jsonObject()
            assertEquals("Statens kartverk", access.string("owner_organization_name"))
            assertEquals("Oslo kommune", access.string("consumer_organization_name"))
        }
    }

    @Test
    fun testScopeAccessAllGet() = testApp {
        client.get("/api/v1/scopes/access/all").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testCreatedScopeCanImmediatelyReceiveAccess() = testApp {
        client.post("/api/v1/scopes") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "prefix": "kartverk",
                  "subscope": "shared-db.read",
                  "description": "Shared database scope",
                  "active": true,
                  "allowed_integration_types": ["maskinporten"],
                  "visibility": "PUBLIC",
                  "token_type": "SELF_CONTAINED",
                  "at_max_age": 120,
                  "requires_user_authentication": false,
                  "requires_pseudonymous_tokens": false,
                  "requires_user_consent": false,
                  "accessible_for_all": true
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.put("/api/v1/scopes/access/971032081?scope=kartverk:shared-db.read").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("kartverk:shared-db.read"))
        }

        client.get("/api/v1/scopes/access?consumer_orgno=971032081&scope=kartverk:shared-db.read").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("kartverk:shared-db.read"))
        }
    }
}
