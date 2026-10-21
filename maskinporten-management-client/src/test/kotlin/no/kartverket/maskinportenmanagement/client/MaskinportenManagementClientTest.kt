package no.kartverket.maskinportenmanagement.client

import kotlinx.coroutines.runBlocking
import no.kartverket.maskinportenmanagement.client.auth.AccessToken
import no.kartverket.maskinportenmanagement.client.auth.AccessTokenProvider
import no.kartverket.maskinportenmanagement.client.filtering.ScopeFilter
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

class MaskinportenManagementClientTest {

    private val sent = mutableListOf<OutgoingRequest>()

    private fun clientAnswering(
        baseUrl: URI = URI("https://digdir.test"),
        answer: (OutgoingRequest) -> OutgoingResponse = { OutgoingResponse(200, "application/json", "[]".toByteArray()) },
    ) = MaskinportenManagementClient(
        baseUrl,
        OutgoingHttpClient { request ->
            sent += request
            answer(request)
        },
        { AccessToken.of("test-token") },
    )

    private fun answering(body: String) = clientAnswering { OutgoingResponse(200, "application/json", body.toByteArray()) }

    private val filter = ScopeFilter(exact = listOf("kartverk:nrl.rapportering"), prefix = listOf("kartverk:tilgangsstyring/"))

    @Test
    fun `a client made for an environment calls Digdir in that environment`() = runBlocking {
        for (environment in DigdirEnvironment.entries) {
            sent.clear()
            val httpClient = OutgoingHttpClient { request ->
                sent += request
                OutgoingResponse(200, "application/json", "[]".toByteArray())
            }

            MaskinportenManagementClient(environment, httpClient) { AccessToken.of("test-token") }.listScopes()

            assertEquals("${environment.digdirBaseUrl}/api/v1/scopes", sent.single().url.toString(), environment.name)
        }
    }

    @Test
    fun `lists Kartverket's scopes without a query`() = runBlocking {
        clientAnswering().listScopes()

        val request = sent.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("https://digdir.test/api/v1/scopes", request.url.toString())
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `reads every field of Digdir's scope, and leaves out the fields it does not know`() = runBlocking {
        val scope = """{
            "name":"kartverk:tilgangsstyring/demo.read","prefix":"kartverk","subscope":"tilgangsstyring/demo.read",
            "description":"Lesetilgang","long_description":"Lang lesetilgang","description#en":"Read access",
            "description#nn":"Lesetilgang på nynorsk","long_description#en":"Long read access",
            "long_description#nn":"Lang lesetilgang på nynorsk","description#se":"Lohkanlobi",
            "long_description#se":"Lang lesetilgang på samisk","active":true,
            "created":"2026-01-15T09:30:00.123456Z","last_updated":"2026-02-01T10:00:00Z",
            "allowed_integration_types":["maskinporten","api_klient"],"owner_orgno":"971040238","token_type":"OPAQUE",
            "visibility":"PRIVATE","requires_user_consent":true,"requires_user_authentication":true,
            "requires_pseudonymous_tokens":true,"delegation_source":"https://www.altinn.no/","at_max_age":120,
            "authorization_max_lifetime":7200,"accessible_for_all":true,"enforced_aud_for_access_token":"https://api.test",
            "protected":true,"supports_european_businesses":true,"new_field":"from Digdir"
        }"""

        assertEquals(
            listOf(
                ScopeDetails(
                    name = Scope.of("kartverk:tilgangsstyring/demo.read"),
                    prefix = "kartverk",
                    subscope = "tilgangsstyring/demo.read",
                    description = "Lesetilgang",
                    longDescription = "Lang lesetilgang",
                    descriptionEn = "Read access",
                    descriptionNn = "Lesetilgang på nynorsk",
                    descriptionSe = "Lohkanlobi",
                    longDescriptionEn = "Long read access",
                    longDescriptionNn = "Lang lesetilgang på nynorsk",
                    longDescriptionSe = "Lang lesetilgang på samisk",
                    active = true,
                    created = Instant.parse("2026-01-15T09:30:00.123456Z"),
                    lastUpdated = Instant.parse("2026-02-01T10:00:00Z"),
                    allowedIntegrationTypes = listOf(IntegrationType.MASKINPORTEN, IntegrationType.API_KLIENT),
                    ownerOrgno = OrganizationNumber.of("971040238"),
                    tokenType = TokenType.OPAQUE,
                    visibility = Visibility.PRIVATE,
                    requiresUserConsent = true,
                    requiresUserAuthentication = true,
                    requiresPseudonymousTokens = true,
                    delegationSource = "https://www.altinn.no/",
                    atMaxAge = 120,
                    authorizationMaxLifetime = 7200,
                    accessibleForAll = true,
                    enforcedAudForAccessToken = "https://api.test",
                    protected = true,
                    supportsEuropeanBusinesses = true,
                ),
            ),
            answering("[$scope]").listScopes(),
        )
    }

    @Test
    fun `with a filter, keeps only the scopes it allows`() = runBlocking {
        val scopes = jsonList(
            digdirScope("kartverk:tilgangsstyring/demo.read"),
            digdirScope("kartverk:dokumentbestilling"),
            digdirScope("kartverk:nrl.rapportering"),
        )

        assertEquals(
            listOf("kartverk:tilgangsstyring/demo.read", "kartverk:nrl.rapportering"),
            answering(scopes).listScopes(filter).map { it.name.value },
        )
    }

    @Test
    fun `an answer this client cannot read becomes a DigdirException`() {
        val answers = listOf(
            "not json",
            "",
            digdirScope("kartverk:nrl.rapportering"),
            "[" + digdirScope("kartverk:nrl.rapportering").replace(""""description":"Tilgang til kartverk:nrl.rapportering",""", "") + "]",
            "[" + digdirScope("kartverk:nrl.rapportering").replace("971040238", "97104023") + "]",
            jsonList(digdirScope("kartverk:nrl rapportering")),
        )
        for (answer in answers) {
            val e = assertFailsWith<DigdirException>(answer) { runBlocking { answering(answer).listScopes(filter) } }

            assertContains(e.message!!, "cannot read", message = answer)
        }
    }

    @Test
    fun `a value Digdir has added is read as UNKNOWN`() = runBlocking {
        val scope = digdirScope("kartverk:nrl.rapportering")
            .replace("SELF_CONTAINED", "BRAND_NEW")
            .replace("PUBLIC", "SECRET")
            .replace(""""allowed_integration_types":["maskinporten"]""", """"allowed_integration_types":["maskinporten","new_portal"]""")

        val read = answering("[$scope]").listScopes().single()

        assertEquals(TokenType.UNKNOWN, read.tokenType)
        assertEquals(Visibility.UNKNOWN, read.visibility)
        assertEquals(listOf(IntegrationType.MASKINPORTEN, IntegrationType.UNKNOWN), read.allowedIntegrationTypes)

        val access = digdirScopeAccess("kartverk:matrikkel.read").replace("APPROVED", "PENDING")
        assertEquals(
            ScopeAccessState.UNKNOWN,
            answering(access).grantScopeAccess(OrganizationNumber.of("311718371"), Scope.of("kartverk:matrikkel.read")).state,
        )
    }

    @Test
    fun `creates a scope by posting its JSON as it is, and returns it`() = runBlocking {
        val json = """{"prefix":"kartverk","subscope":"tilgangsstyring/demo.read","description":"Lesetilgang, æøå"}"""

        val created = answering(digdirScope("kartverk:tilgangsstyring/demo.read")).createScope(ScopeJson.of(json))

        val request = sent.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("https://digdir.test/api/v1/scopes", request.url.toString())
        assertEquals("application/json", request.headers["Content-Type"])
        assertContentEquals(json.toByteArray(Charsets.UTF_8), request.body)
        assertEquals(Scope.of("kartverk:tilgangsstyring/demo.read"), created.name)
    }

    @Test
    fun `a call without a body sends no body and no Content-Type`() = runBlocking {
        clientAnswering().listScopes()

        val request = sent.single()
        assertNull(request.body)
        assertNull(request.headers["Content-Type"])
    }

    @Test
    fun `updates a scope by putting its JSON as it is, with the scope in the query, and returns it`() = runBlocking {
        val json = """{"prefix":"kartverk","subscope":"tilgangsstyring/demo.read","description":"Lesetilgang, æøå"}"""

        val updated = answering(digdirScope("kartverk:tilgangsstyring/demo.read"))
            .updateScope(Scope.of("kartverk:tilgangsstyring/demo.read"), ScopeUpdateJson.of(json))

        val request = sent.single()
        assertEquals(HttpMethod.PUT, request.method)
        assertEquals("https://digdir.test/api/v1/scopes?scope=kartverk%3Atilgangsstyring%2Fdemo.read", request.url.toString())
        assertEquals("application/json", request.headers["Content-Type"])
        assertContentEquals(json.toByteArray(Charsets.UTF_8), request.body)
        assertEquals(Scope.of("kartverk:tilgangsstyring/demo.read"), updated.name)
    }

    @Test
    fun `deletes a scope with only the scope in the query, and returns it`() = runBlocking {
        val deleted = answering(digdirScope("kartverk:tilgangsstyring/demo.read").replace("\"active\":true", "\"active\":false"))
            .deleteScope(Scope.of("kartverk:tilgangsstyring/demo.read"))

        val request = sent.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals("https://digdir.test/api/v1/scopes?scope=kartverk%3Atilgangsstyring%2Fdemo.read", request.url.toString())
        assertEquals(Scope.of("kartverk:tilgangsstyring/demo.read"), deleted.name)
        assertEquals(false, deleted.active)
    }

    @Test
    fun `lists scope access with only the scope in the query`() = runBlocking {
        clientAnswering().listScopeAccess(Scope.of("kartverk:matrikkel.read"))

        val request = sent.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("https://digdir.test/api/v1/scopes/access?scope=kartverk%3Amatrikkel.read", request.url.toString())
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("Bearer test-token", request.headers["Authorization"])
    }

    @Test
    fun `a scope cannot add query parameters of its own`() = runBlocking {
        clientAnswering().listScopeAccess(Scope.of("kartverk:x&consumer_orgno=123"))

        assertEquals("scope=kartverk%3Ax%26consumer_orgno%3D123", sent.single().url.rawQuery)
    }

    private val consumerOrgno = OrganizationNumber.of("311718371")

    @Test
    fun `lists a consumer's scope access with only the organisation in the query`() = runBlocking {
        clientAnswering().listConsumerScopeAccess(consumerOrgno)

        val request = sent.single()
        assertEquals(HttpMethod.GET, request.method)
        assertEquals("https://digdir.test/api/v1/scopes/access?consumer_orgno=311718371", request.url.toString())
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `with a filter, keeps only the consumer's scope access whose scope it allows`() = runBlocking {
        val access = jsonList(
            digdirScopeAccess("kartverk:tilgangsstyring/demo.read"),
            digdirScopeAccess("kartverk:dokumentbestilling"),
            digdirScopeAccess("kartverk:nrl.rapportering"),
        )

        assertEquals(
            listOf("kartverk:tilgangsstyring/demo.read", "kartverk:nrl.rapportering"),
            answering(access).listConsumerScopeAccess(consumerOrgno, filter).map { it.scope.value },
        )
    }

    @Test
    fun `reads every field of Digdir's scope access`() = runBlocking {
        assertEquals(
            ScopeAccess(
                scope = Scope.of("kartverk:matrikkel.read"),
                ownerOrgno = OrganizationNumber.of("971040238"),
                ownerOrganizationName = "STATENS KARTVERK",
                consumerOrgno = consumerOrgno,
                consumerOrganizationName = "TESTORGANISASJON",
                state = ScopeAccessState.APPROVED,
                created = Instant.parse("2026-01-15T09:30:00Z"),
                lastUpdated = Instant.parse("2026-01-15T09:30:00Z"),
            ),
            answering(digdirScopeAccess("kartverk:matrikkel.read")).grantScopeAccess(consumerOrgno, Scope.of("kartverk:matrikkel.read")),
        )
    }

    @Test
    fun `reads scope access that has no organisation names`() = runBlocking {
        val access = digdirScopeAccess("kartverk:matrikkel.read")
            .replace(""""owner_organization_name":"STATENS KARTVERK",""", "")
            .replace(""""consumer_organization_name":"TESTORGANISASJON",""", "")

        val read = answering(access).grantScopeAccess(consumerOrgno, Scope.of("kartverk:matrikkel.read"))

        assertEquals(null, read.ownerOrganizationName)
        assertEquals(null, read.consumerOrganizationName)
    }

    @Test
    fun `removes scope access with the organisation in the path and only the scope in the query`() = runBlocking {
        answering(digdirScopeAccess("kartverk:matrikkel.read")).removeScopeAccess(consumerOrgno, Scope.of("kartverk:matrikkel.read"))

        val request = sent.single()
        assertEquals(HttpMethod.DELETE, request.method)
        assertEquals(
            "https://digdir.test/api/v1/scopes/access/311718371?scope=kartverk%3Amatrikkel.read",
            request.url.toString(),
        )
    }

    @Test
    fun `grants scope access with the organisation in the path and only the scope in the query`() = runBlocking {
        answering(digdirScopeAccess("kartverk:matrikkel.read")).grantScopeAccess(consumerOrgno, Scope.of("kartverk:matrikkel.read"))

        val request = sent.single()
        assertEquals(HttpMethod.PUT, request.method)
        assertEquals(
            "https://digdir.test/api/v1/scopes/access/311718371?scope=kartverk%3Amatrikkel.read",
            request.url.toString(),
        )
    }

    @Test
    fun `gets a scope's access changelog with the scope, and the page and size only when given`() = runBlocking {
        val client = answering(digdirScopeAccessChangelog())
        val scope = Scope.of("kartverk:matrikkel.read")

        client.getScopeAccessChangelog(scope)
        client.getScopeAccessChangelog(scope, page = 2)
        client.getScopeAccessChangelog(scope, page = 0, size = 50)

        assertEquals(
            listOf(
                "https://digdir.test/api/v1/scopes/access/changelog?scopeName=kartverk%3Amatrikkel.read",
                "https://digdir.test/api/v1/scopes/access/changelog?scopeName=kartverk%3Amatrikkel.read&page=2",
                "https://digdir.test/api/v1/scopes/access/changelog?scopeName=kartverk%3Amatrikkel.read&page=0&size=50",
            ),
            sent.map { it.url.toString() },
        )
        assertEquals(listOf(HttpMethod.GET), sent.map { it.method }.distinct())
    }

    @Test
    fun `reads every field of Digdir's access changelog, and an entry without the fields for people to read`() = runBlocking {
        val change = ScopeAccessChange(
            id = 7,
            created = Instant.parse("2026-01-15T09:30:00Z"),
            message = "Scope access created",
            actorName = "Kartverket",
            channel = "API",
            diff = """{"consumer_orgno":"311718371","state":"APPROVED"}""",
        )
        val bare = """{"id":8,"created":"2026-01-16T10:00:00Z","new_field":"from Digdir"}"""
        val changelog = digdirScopeAccessChangelog().replace("}],", "},$bare],")

        assertEquals(
            ScopeAccessChangelog(
                content = listOf(change, ScopeAccessChange(id = 8, created = Instant.parse("2026-01-16T10:00:00Z"))),
                page = PageMetadata(size = 20, number = 0, totalElements = 1, totalPages = 1),
            ),
            answering(changelog).getScopeAccessChangelog(Scope.of("kartverk:matrikkel.read")),
        )
    }

    @Test
    fun `refuses a page below 0 and a size below 1 without calling Digdir`() {
        val scope = Scope.of("kartverk:matrikkel.read")

        assertFailsWith<IllegalArgumentException> { runBlocking { clientAnswering().getScopeAccessChangelog(scope, page = -1) } }
        assertFailsWith<IllegalArgumentException> { runBlocking { clientAnswering().getScopeAccessChangelog(scope, size = 0) } }
        assertEquals(emptyList<OutgoingRequest>(), sent)
    }

    @Test
    fun `a trailing slash on the base URL is ignored`() = runBlocking {
        clientAnswering(baseUrl = URI("https://digdir.test/")).listScopeAccess(Scope.of("kartverk:x"))

        assertEquals("/api/v1/scopes/access", sent.single().url.path)
    }

    @Test
    fun `an error from Digdir becomes a DigdirErrorResponseException that holds Digdir's answer`() {
        for (status in listOf(302, 400, 403, 404, 500)) {
            val answer = OutgoingResponse(status, "application/json;charset=UTF-8", """{"status":$status,"error":"æøå"}""".toByteArray())

            val e = assertFailsWith<DigdirErrorResponseException>("for $status") {
                runBlocking { clientAnswering { answer }.listScopeAccess(Scope.of("kartverk:x")) }
            }

            assertEquals(status, e.statusCode)
            assertEquals("application/json;charset=UTF-8", e.contentType)
            assertContentEquals(answer.body, e.body)
        }
    }

    @Test
    fun `a 401 from Digdir becomes a DigdirException, and the token is not used again`() {
        val refused = mutableListOf<AccessToken>()
        val client = MaskinportenManagementClient(
            URI("https://digdir.test"),
            { OutgoingResponse(401, "application/json", """{"error":"invalid_token"}""".toByteArray()) },
            object : AccessTokenProvider {
                override suspend fun accessToken() = AccessToken.of("test-token")
                override suspend fun refused(token: AccessToken) {
                    refused += token
                }
            },
        )

        val e = assertFailsWith<DigdirException> { runBlocking { client.listScopeAccess(Scope.of("kartverk:x")) } }

        assertContains(e.message!!, "401")
        assertEquals(listOf(AccessToken.of("test-token")), refused)
    }

    @Test
    fun `a call Digdir never answered becomes a DigdirException`() {
        val failure = IOException("Connection refused")

        val e = assertFailsWith<DigdirException> {
            runBlocking { clientAnswering { throw failure }.listScopeAccess(Scope.of("kartverk:x")) }
        }

        assertSame(failure, e.cause)
    }

    @Test
    fun `the DigdirException names the failure, even when it has no message`() {
        val e = assertFailsWith<DigdirException> {
            runBlocking { clientAnswering { throw ConnectException() }.listScopeAccess(Scope.of("kartverk:x")) }
        }

        assertContains(e.message!!, "ConnectException")
    }
}
