package com.maskinporten

import com.maskinporten.repositories.changelog.isDescending
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangelogTest {

    @Test
    fun testScopeAccessChangelogGet() = testApp {
        client.get("/api/v1/scopes/access/changelog?scopeName=kartverk:matrikkel.read&page=0&size=10&sort=asc").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testScopeAccessChangelogGetDesc() = testApp {
        client.get("/api/v1/scopes/access/changelog?scopeName=kartverk:matrikkel.read&page=0&size=10&sort=desc").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testChangelogPagingParamsAreOptional() = testApp {
        client.get("/api/v1/scopes/access/changelog?scopeName=kartverk:matrikkel.read").apply {
            assertEquals(HttpStatusCode.OK, status)
            val page = jsonObject()["page"]!!.jsonObject
            assertEquals("20", page.string("size"))
            assertEquals("0", page.string("number"))
            assertEquals("1", page.string("totalElements"))
        }
        client.get("/api/v1/scopes/access/changelog?scopeName=kartverk:matrikkel.read&sort=created,desc").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testChangelogEntriesHaveSpecFields() = testApp {
        client.get("/api/v1/scopes/access/changelog?scopeName=kartverk:matrikkel.read").apply {
            val entry = jsonObject()["content"]!!.jsonArray.first().jsonObject
            assertEquals(setOf("id", "created", "message", "actor_name", "channel", "diff"), entry.keys)
        }
    }

    @Test
    fun testChangelogRejectsInvalidSize() = testApp {
        client.get("/api/v1/scopes/access/changelog?scopeName=kartverk:matrikkel.read&size=0").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun testChangelogRequiresScopeNameQueryParam() = testApp {
        client.get("/api/v1/scopes/access/changelog").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
        client.get("/api/v1/scopes/access/kartverk:matrikkel.read/changelog").apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun testSortDirectionIsParsedFromCriterion() {
        assertTrue(isDescending(listOf("created,desc")))
        assertTrue(isDescending(listOf("desc")))
        assertFalse(isDescending(listOf("created,asc")))
        assertFalse(isDescending(listOf("created")))
        assertFalse(isDescending(emptyList()))
    }
}
