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
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScopesTest {

    @Test
    fun testScopesGet() = testApp {
        client.get("/api/v1/scopes").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testTranslationsAreFlatFields() = testApp {
        val scope = client.get("/api/v1/scopes/scope?scope=kartverk:matrikkel.read").jsonObject()
        assertEquals("Read access to cadastre units and address data", scope.string("description#en"))
        assertTrue("long_description#en" in scope)
        assertTrue("descriptions" !in scope)

        val all = client.get("/api/v1/scopes/all").jsonObjects()
        assertTrue(all.none { "descriptions" in it })
        val untranslated = all.first { it.string("name") == "kartverk:grunnbok.read" }
        assertTrue(untranslated.keys.none { '#' in it })
    }

    @Test
    fun testScopesPost() = testApp {
        client.post("/api/v1/scopes") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "prefix": "kartverk",
                  "subscope": "test.read",
                  "description": "Test scope",
                  "active": true,
                  "allowed_integration_types": ["maskinporten"],
                  "visibility": "PUBLIC",
                  "token_type": "SELF_CONTAINED",
                  "at_max_age": 120,
                  "requires_user_authentication": false,
                  "requires_pseudonymous_tokens": false,
                  "requires_user_consent": false,
                  "accessible_for_all": false
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("kartverk:test.read"))
        }

        val created = client.get("/api/v1/scopes/scope?scope=kartverk:test.read").jsonObject()
        assertTrue("long_description" !in created)
        assertTrue("enforced_aud_for_access_token" !in created)
    }

    @Test
    fun testScopesPostWithOnlyPrefixSubscopeAndDescriptionGetsDefaults() = testApp {
        client.post("/api/v1/scopes") {
            contentType(ContentType.Application.Json)
            setBody("""{ "prefix": "kartverk", "subscope": "test.read", "description": "Test scope" }""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        val created = client.get("/api/v1/scopes/scope?scope=kartverk:test.read").jsonObject()
        assertEquals("true", created.string("active"))
        assertEquals(listOf("maskinporten"), created.strings("allowed_integration_types"))
        assertEquals("PUBLIC", created.string("visibility"))
        assertEquals("SELF_CONTAINED", created.string("token_type"))
        assertEquals("0", created.string("at_max_age"))
        assertEquals("false", created.string("requires_user_authentication"))
        assertEquals("false", created.string("requires_pseudonymous_tokens"))
        assertEquals("false", created.string("requires_user_consent"))
        assertEquals("false", created.string("accessible_for_all"))
    }

    @Test
    fun testScopesPostWithoutDescriptionIsRejected() = testApp {
        client.post("/api/v1/scopes") {
            contentType(ContentType.Application.Json)
            setBody("""{ "prefix": "kartverk", "subscope": "test.read" }""")
        }.apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun testScopesPostWithoutBodyIsRejected() = testApp {
        client.post("/api/v1/scopes").apply {
            assertEquals(HttpStatusCode.UnsupportedMediaType, status)
        }
    }

    @Test
    fun testScopesPut() = testApp {
        client.put("/api/v1/scopes?scope=kartverk:matrikkel.read") {
            contentType(ContentType.Application.Json)
            setBody("""{ "description": "Oppdatert i test" }""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("Oppdatert i test"))
        }
    }

    @Test
    fun testScopesPutAcceptsTheScopeAsItWasRead() = testApp {
        val scope = client.get("/api/v1/scopes/scope?scope=kartverk:matrikkel.read").jsonObject()
        val changed = JsonObject(scope + ("description" to JsonPrimitive("Endret etter henting")))
        client.put("/api/v1/scopes?scope=kartverk:matrikkel.read") {
            contentType(ContentType.Application.Json)
            setBody(changed.toString())
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Endret etter henting", jsonObject().string("description"))
        }
    }

    @Test
    fun testScopesDelete() = testApp {
        client.delete("/api/v1/scopes?scope=kartverk:matrikkel.read").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.get("/api/v1/scopes/scope?scope=kartverk:matrikkel.read").apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun testScopesDeleteWithoutScopeIsRejected() = testApp {
        client.delete("/api/v1/scopes").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun testScopesScopeGet() = testApp {
        client.get("/api/v1/scopes/scope?scope=kartverk:matrikkel.read").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("kartverk:matrikkel.read"))
        }
    }

    @Test
    fun testScopesAllGet() = testApp {
        client.get("/api/v1/scopes/all").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testScopesAllGetFilteredByIntegrationType() = testApp {
        val all = client.get("/api/v1/scopes/all").jsonObjects()
        client.get("/api/v1/scopes/all?integration_type=API_CLIENT").apply {
            assertEquals(HttpStatusCode.OK, status)
            val scopes = jsonObjects()
            assertTrue(scopes.isNotEmpty())
            assertTrue(scopes.size < all.size)
            assertTrue(scopes.all { "api_klient" in it.strings("allowed_integration_types") })
        }
    }

    @Test
    fun testScopesAllGetFilteredByPrefix() = testApp {
        client.get("/api/v1/scopes/all?prefix=kartverk").apply {
            assertEquals(HttpStatusCode.OK, status)
            val scopes = jsonObjects()
            assertTrue(scopes.isNotEmpty())
            assertTrue(scopes.all { it.string("prefix") == "kartverk" })
        }
        client.get("/api/v1/scopes/all?prefix=finnes-ikke").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(jsonObjects().isEmpty())
        }
    }

    @Test
    fun testScopesAllGetFilteredByDelegatedSourcesAndAccessibleForAll() = testApp {
        client.get("/api/v1/scopes/all?delegated_sources=true").apply {
            assertEquals(HttpStatusCode.OK, status)
            val scopes = jsonObjects()
            assertTrue(scopes.isNotEmpty())
            assertTrue(scopes.all { it.string("delegation_source") != null })
        }
        client.get("/api/v1/scopes/all?accessible_for_all=true").apply {
            assertEquals(HttpStatusCode.OK, status)
            val scopes = jsonObjects()
            assertTrue(scopes.isNotEmpty())
            assertTrue(scopes.all { it.string("accessible_for_all") == "true" })
        }
    }

    @Test
    fun testInactiveScopesAreOnlyReturnedWhenRequested() = testApp {
        client.put("/api/v1/scopes?scope=kartverk:matrikkel.read") {
            contentType(ContentType.Application.Json)
            setBody("""{ "active": false }""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("false", jsonObject().string("active"))
        }

        for (path in listOf("/api/v1/scopes", "/api/v1/scopes/all")) {
            val names = client.get(path).jsonObjects().map { it.string("name") }
            assertTrue("kartverk:matrikkel.read" !in names, path)

            val namesWithInactive = client.get("$path?inactive=true").jsonObjects().map { it.string("name") }
            assertTrue("kartverk:matrikkel.read" in namesWithInactive, path)
        }
    }

    @Test
    fun testScopesPutUpdatesAllFields() = testApp {
        client.put("/api/v1/scopes?scope=kartverk:matrikkel.read") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "long_description": "Ny lang beskrivelse",
                  "owner_orgno": "991825827",
                  "token_type": "OPAQUE",
                  "authorization_max_lifetime": 999
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val scope = jsonObject()
            assertEquals("kartverk:matrikkel.read", scope.string("name"))
            assertEquals("Ny lang beskrivelse", scope.string("long_description"))
            assertEquals("991825827", scope.string("owner_orgno"))
            assertEquals("OPAQUE", scope.string("token_type"))
            assertEquals("999", scope.string("authorization_max_lifetime"))
        }
    }

    // Digdir does not let a scope be renamed
    @Test
    fun testScopesPutCannotChangePrefixOrSubscope() = testApp {
        for (body in listOf("""{ "subscope": "matrikkel.les" }""", """{ "prefix": "statens-kartverk" }""")) {
            client.put("/api/v1/scopes?scope=kartverk:matrikkel.read") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, status, body)
            }
        }

        client.get("/api/v1/scopes/scope?scope=kartverk:matrikkel.read").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testScopesPutAcceptsUnchangedPrefixAndSubscope() = testApp {
        client.put("/api/v1/scopes?scope=kartverk:matrikkel.read") {
            contentType(ContentType.Application.Json)
            setBody("""{ "prefix": "kartverk", "subscope": "matrikkel.read", "description": "Uendret navn" }""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("kartverk:matrikkel.read", jsonObject().string("name"))
        }
    }

    @Test
    fun testScopesPostKeepsTranslations() = testApp {
        client.post("/api/v1/scopes") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "prefix": "kartverk",
                  "subscope": "test.read",
                  "description": "Testscope",
                  "description#en": "Test scope",
                  "long_description#nn": "Lang skildring",
                  "active": true,
                  "allowed_integration_types": ["maskinporten"],
                  "visibility": "PUBLIC",
                  "token_type": "SELF_CONTAINED",
                  "at_max_age": 120,
                  "requires_user_authentication": false,
                  "requires_pseudonymous_tokens": false,
                  "requires_user_consent": false,
                  "accessible_for_all": false
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        val created = client.get("/api/v1/scopes/scope?scope=kartverk:test.read").jsonObject()
        assertEquals("Test scope", created.string("description#en"))
        assertEquals("Lang skildring", created.string("long_description#nn"))
    }

    @Test
    fun testScopesPutChangesOnlyTheTranslationsItNames() = testApp {
        client.put("/api/v1/scopes?scope=kartverk:matrikkel.read") {
            contentType(ContentType.Application.Json)
            setBody("""{ "description#en": "Read cadastre data", "description#nn": "Lesetilgang til matrikkelen" }""")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        val scope = client.get("/api/v1/scopes/scope?scope=kartverk:matrikkel.read").jsonObject()
        assertEquals("Read cadastre data", scope.string("description#en"))
        assertEquals("Lesetilgang til matrikkelen", scope.string("description#nn"))
        assertEquals(
            "Used for lookup in cadastre data such as property, address and building references.",
            scope.string("long_description#en"),
        )
    }

    @Test
    fun testScopesPostDuplicateReturnsConflict() = testApp {
        client.post("/api/v1/scopes") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "prefix": "kartverk",
                  "subscope": "matrikkel.read",
                  "description": "Finnes allerede",
                  "active": true,
                  "allowed_integration_types": ["maskinporten"],
                  "visibility": "PUBLIC",
                  "token_type": "SELF_CONTAINED",
                  "at_max_age": 120,
                  "requires_user_authentication": false,
                  "requires_pseudonymous_tokens": false,
                  "requires_user_consent": false,
                  "accessible_for_all": false
                }
                """.trimIndent()
            )
        }.apply {
            assertEquals(HttpStatusCode.Conflict, status)
        }
    }

    @Test
    fun testScopesDeleteReturnsDeletedScope() = testApp {
        client.delete("/api/v1/scopes?scope=kartverk:matrikkel.read").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("kartverk:matrikkel.read", jsonObject().string("name"))
        }
    }
}
