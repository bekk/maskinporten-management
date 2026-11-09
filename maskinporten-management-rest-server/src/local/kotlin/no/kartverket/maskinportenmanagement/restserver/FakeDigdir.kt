package no.kartverket.maskinportenmanagement.restserver

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.kartverket.maskinportenmanagement.client.ScopeDetails
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import java.net.URLDecoder
import java.time.Instant

// Plays the part of Digdir's API that the client calls, in memory, and takes any token, so a local run needs no
// Maskinporten client or key. Like Digdir, it keeps a deleted scope and removed access, only inactive.
internal class FakeDigdir : OutgoingHttpClient {
    private val mutex = Mutex()
    private val scopes = linkedMapOf<String, JsonObject>()
    private val access = mutableListOf<Access>()
    private val changelog = mutableListOf<JsonObject>()

    init {
        seededScopes.forEach { (name, description) -> scopes[name] = newScope(name, description) }
        seededAccess.forEach { (scope, orgno) -> grant(scope, orgno) }
        changelog.clear()
    }

    override suspend fun send(request: OutgoingRequest): OutgoingResponse = mutex.withLock {
        val path = request.url.path
        val query = request.url.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
            val (name, value) = (it.split('=', limit = 2) + "")
            name to URLDecoder.decode(value, Charsets.UTF_8)
        }
        val body = request.body?.let { runCatching { Json.parseToJsonElement(it.decodeToString()) }.getOrNull() as? JsonObject }
        when {
            path == SCOPES -> when (request.method) {
                HttpMethod.GET -> ok(JsonArray(scopes.values.filter { it.isActive }))

                HttpMethod.POST -> body?.let(::create) ?: badRequest("The body must be a scope")

                HttpMethod.PUT -> body?.let { update(query["scope"], it) } ?: badRequest("The body must be a scope")

                HttpMethod.DELETE -> withScope(query["scope"]) { name ->
                    stored(name, changed(name, mapOf("active" to JsonPrimitive(false))))
                }
            }

            path == ACCESS && request.method == HttpMethod.GET -> {
                val scope = query["scope"]
                val consumer = query["consumer_orgno"]
                ok(JsonArray(access.filter { it.active && (it.scope == scope || it.consumerOrgno == consumer) }.map { it.json }))
            }

            path == "$ACCESS/changelog" && request.method == HttpMethod.GET -> withScope(query["scopeName"]) { name ->
                changelogPage(name, query["page"]?.toInt() ?: 0, query["size"]?.toInt() ?: 20)
            }

            path.startsWith("$ACCESS/") -> {
                val consumer = path.removePrefix("$ACCESS/")
                withScope(query["scope"]) { name ->
                    when (request.method) {
                        HttpMethod.PUT -> ok(grant(name, consumer).json)

                        HttpMethod.DELETE -> access.firstOrNull { it.active && it.scope == name && it.consumerOrgno == consumer }
                            ?.let { ok(remove(it).json) }
                            ?: notFound("$consumer has no access to $name")

                        else -> notFound("No such endpoint")
                    }
                }
            }

            else -> notFound("No such endpoint")
        }
    }

    private fun create(body: JsonObject): OutgoingResponse {
        val prefix = (body["prefix"] as? JsonPrimitive)?.content
        val subscope = (body["subscope"] as? JsonPrimitive)?.content
        if (prefix.isNullOrBlank() || subscope.isNullOrBlank()) return badRequest("prefix and subscope are required")
        if (prefix != KARTVERK_PREFIX) return answer(403, "access_denied", "Kartverket does not own the prefix $prefix")
        val name = "$prefix:$subscope"
        if (name in scopes) return answer(409, "conflict", "The scope $name already exists")
        return stored(name, JsonObject(newScope(name, "") + body.filterKeys { it !in DIGDIR_SETS }))
    }

    private fun update(name: String?, body: JsonObject): OutgoingResponse = withScope(name) { found ->
        stored(found, changed(found, body.filterKeys { it !in DIGDIR_SETS }))
    }

    private fun changed(name: String, fields: Map<String, JsonElement>): JsonObject =
        JsonObject(scopes.getValue(name) + fields + ("last_updated" to JsonPrimitive(now())))

    // Digdir refuses a scope that does not fit its model, which ScopeDetails mirrors
    private fun stored(name: String, scope: JsonObject): OutgoingResponse {
        if (runCatching { ignoringUnknownKeys.decodeFromJsonElement(ScopeDetails.serializer(), scope) }.isFailure) {
            return badRequest("The scope does not fit Digdir's model")
        }
        scopes[name] = scope
        return ok(scope)
    }

    private fun grant(scope: String, consumerOrgno: String): Access {
        val existing = access.firstOrNull { it.scope == scope && it.consumerOrgno == consumerOrgno }
        val now = now()
        val granted = Access(scope, consumerOrgno, created = existing?.created ?: now, lastUpdated = now)
        existing?.let(access::remove)
        access += granted
        log(scope, "Scope access created", consumerOrgno, "APPROVED")
        return granted
    }

    private fun remove(removed: Access): Access {
        val inactive = removed.copy(active = false, lastUpdated = now())
        access[access.indexOf(removed)] = inactive
        log(removed.scope, "Scope access deleted", removed.consumerOrgno, "DENIED")
        return inactive
    }

    private fun log(scope: String, message: String, consumerOrgno: String, state: String) {
        changelog += buildJsonObject {
            put("id", changelog.size + 1)
            put("scope", scope)
            put("created", now())
            put("message", message)
            put("actor_name", "STATENS KARTVERK")
            put("channel", "API")
            put("diff", """{"consumer_orgno":"$consumerOrgno","state":"$state"}""")
        }
    }

    private fun changelogPage(scope: String, page: Int, size: Int): OutgoingResponse {
        val entries = changelog.filter { (it["scope"] as JsonPrimitive).content == scope }.map { JsonObject(it - "scope") }
        return ok(
            buildJsonObject {
                put("content", JsonArray(entries.drop(page * size).take(size)))
                put(
                    "page",
                    buildJsonObject {
                        put("size", size)
                        put("number", page)
                        put("totalElements", entries.size)
                        put("totalPages", (entries.size + size - 1) / size)
                    },
                )
            },
        )
    }

    private fun withScope(name: String?, answer: (String) -> OutgoingResponse): OutgoingResponse =
        if (name != null && scopes[name]?.isActive == true) answer(name) else notFound("There is no scope $name")

    private data class Access(
        val scope: String,
        val consumerOrgno: String,
        val created: String,
        val lastUpdated: String,
        val active: Boolean = true,
    ) {
        val json: JsonObject get() = buildJsonObject {
            put("scope", scope)
            put("owner_orgno", KARTVERKET)
            put("owner_organization_name", "STATENS KARTVERK")
            put("consumer_orgno", consumerOrgno)
            put("consumer_organization_name", organizationNames[consumerOrgno] ?: "ORGANISASJON $consumerOrgno")
            put("state", if (active) "APPROVED" else "DENIED")
            put("created", created)
            put("last_updated", lastUpdated)
        }
    }

    private companion object {
        const val SCOPES = "/api/v1/scopes"
        const val ACCESS = "/api/v1/scopes/access"
        const val KARTVERKET = "971040238"
        const val KARTVERK_PREFIX = "kartverk"

        // Fields Digdir sets itself or takes from the scope's name, and ignores in a body
        val DIGDIR_SETS = setOf("name", "prefix", "subscope", "created", "last_updated", "owner_orgno")

        val seededScopes = mapOf(
            "kartverk:matrikkel.read" to "Lesetilgang til matrikkelenheter og adresseinformasjon",
            "kartverk:matrikkel.write" to "Skrivetilgang til matrikkeloppdateringer",
            "kartverk:adresse.read" to "Lesetilgang til offisielle adresser",
            "kartverk:grunnbok.read" to "Lesetilgang til grunnboksopplysninger",
            "kartverk:tilgangsstyring/demo.read" to "Lesetilgang til demo-API-et til Team Tilgangsstyring",
            "kartverk:tilgangsstyring/demo.write" to "Skrivetilgang til demo-API-et til Team Tilgangsstyring",
        )

        val seededAccess = listOf(
            "kartverk:matrikkel.read" to "971032081",
            "kartverk:adresse.read" to "971032081",
            "kartverk:adresse.read" to "942110464",
            "kartverk:tilgangsstyring/demo.read" to "311718371",
        )

        val organizationNames = mapOf(
            "971032081" to "STATENS VEGVESEN",
            "942110464" to "TRONDHEIM KOMMUNE",
            "311718371" to "TESTORGANISASJON",
        )

        val ignoringUnknownKeys = Json { ignoreUnknownKeys = true }

        val JsonObject.isActive: Boolean get() = this["active"] == JsonPrimitive(true)

        fun newScope(name: String, description: String) = buildJsonObject {
            put("name", name)
            put("prefix", name.substringBefore(':'))
            put("subscope", name.substringAfter(':'))
            put("description", description)
            put("active", true)
            put("created", now())
            put("last_updated", now())
            put("allowed_integration_types", buildJsonArray { add(JsonPrimitive("maskinporten")) })
            put("owner_orgno", KARTVERKET)
            put("token_type", "SELF_CONTAINED")
            put("visibility", "PUBLIC")
            put("requires_user_consent", false)
            put("requires_user_authentication", false)
            put("requires_pseudonymous_tokens", false)
            put("at_max_age", 120)
            put("authorization_max_lifetime", 0)
            put("accessible_for_all", false)
            put("protected", false)
            put("supports_european_businesses", false)
        }

        fun now(): String = Instant.now().toString()

        fun ok(body: JsonElement) = OutgoingResponse(200, "application/json", body.toString().encodeToByteArray())

        fun notFound(description: String) = answer(404, "not_found", description)

        fun badRequest(description: String) = answer(400, "invalid_request", description)

        fun answer(status: Int, error: String, description: String) = OutgoingResponse(
            status,
            "application/json",
            buildJsonObject {
                put("error", error)
                put("error_description", description)
            }.toString().encodeToByteArray(),
        )
    }
}
