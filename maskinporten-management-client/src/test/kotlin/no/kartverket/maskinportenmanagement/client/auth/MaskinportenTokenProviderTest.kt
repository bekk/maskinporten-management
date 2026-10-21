package no.kartverket.maskinportenmanagement.client.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import no.kartverket.maskinportenmanagement.client.DigdirEnvironment
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.client.kms.LocalKms
import no.kartverket.maskinportenmanagement.client.kms.certificateChain
import no.kartverket.maskinportenmanagement.client.kms.pem
import no.kartverket.maskinportenmanagement.client.kms.rsaKeyPair
import java.net.URI
import java.net.URLDecoder
import java.security.interfaces.RSAPrivateCrtKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MaskinportenTokenProviderTest {

    private val keyPair = rsaKeyPair()
    private val chain = certificateChain(keyPair.public)

    private var now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private var metadataRequests = 0
    private val tokenRequests = mutableListOf<OutgoingRequest>()
    private var tokenAnswer = tokenResponse("token-1")

    // While true, each token request waits in heldTokenRequests until the test answers it
    private var holdTokenRequests = false
    private val heldTokenRequests = Channel<CompletableDeferred<OutgoingResponse>>(Channel.UNLIMITED)

    private val maskinporten = OutgoingHttpClient { request ->
        when (request.url.toString()) {
            "https://maskinporten.test/.well-known/oauth-authorization-server" -> OutgoingResponse(
                200,
                "application/json",
                """{"issuer":"https://maskinporten.test/","token_endpoint":"https://maskinporten.test/token","jwks_uri":"x"}""".toByteArray(),
            ).also { metadataRequests++ }

            "https://maskinporten.test/token" -> {
                tokenRequests += request
                if (holdTokenRequests) CompletableDeferred<OutgoingResponse>().also { heldTokenRequests.send(it) }.await() else tokenAnswer
            }

            else -> error("Unexpected request to ${request.url}")
        }
    }

    private fun providerAsking(scopes: List<Scope>) = MaskinportenTokenProvider(
        wellKnownUrl = URI("https://maskinporten.test/.well-known/oauth-authorization-server"),
        clientId = "test-client",
        scopes = scopes,
        kms = LocalKms(keyPair.private as RSAPrivateCrtKey),
        certificateChainPem = chain.joinToString("") { pem(it) },
        httpClient = maskinporten,
        clock = clock,
        context = Dispatchers.Default + fetches,
    )

    private val fetches = Job()
    private val provider = providerAsking(listOf(Scope.of("idporten:scopes.write")))

    @AfterTest
    fun closeProvider() = provider.close()

    @Test
    fun `sends a JWT grant for our client and scopes, with Maskinporten as the audience`() = runBlocking {
        assertEquals("token-1", provider.accessToken().value)

        val request = tokenRequests.single()
        assertEquals(HttpMethod.POST, request.method)
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        val form = request.body!!.decodeToString().split('&').associate {
            val (key, value) = it.split('=', limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", form["grant_type"])

        val claimsJson = decodeJson(form.getValue("assertion").split('.')[1])
        assertEquals("https://maskinporten.test/", claimsJson.getValue("aud").jsonPrimitive.content)
        assertEquals("test-client", claimsJson.getValue("iss").jsonPrimitive.content)
        assertEquals("idporten:scopes.write", claimsJson.getValue("scope").jsonPrimitive.content)
        assertEquals(now.epochSecond, claimsJson.getValue("iat").jsonPrimitive.long)
        assertEquals(now.epochSecond + 120, claimsJson.getValue("exp").jsonPrimitive.long)
        assertTrue(claimsJson.getValue("jti").jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun `asks for all the scopes, space-separated`() = runBlocking {
        providerAsking(listOf(Scope.of("idporten:scopes.read"), Scope.of("idporten:scopes.write"))).accessToken()

        val form = tokenRequests.single().body!!.decodeToString()
        val assertion = URLDecoder.decode(form.substringAfter("assertion="), Charsets.UTF_8)
        val claimsJson = decodeJson(assertion.split('.')[1])
        assertEquals("idporten:scopes.read idporten:scopes.write", claimsJson.getValue("scope").jsonPrimitive.content)
    }

    @Test
    fun `a provider made for an environment asks Maskinporten in that environment`() = runBlocking {
        for (environment in DigdirEnvironment.entries) {
            val asked = mutableListOf<URI>()
            val unavailable = OutgoingHttpClient { request ->
                asked += request.url
                OutgoingResponse(503, null, ByteArray(0))
            }

            MaskinportenTokenProvider(
                environment = environment,
                clientId = "test-client",
                scopes = listOf(Scope.of("idporten:scopes.write")),
                kms = LocalKms(keyPair.private as RSAPrivateCrtKey),
                certificateChainPem = chain.joinToString("") { pem(it) },
                httpClient = unavailable,
            ).use { assertFailsWith<DigdirException> { it.accessToken() } }

            assertEquals(listOf(environment.maskinportenWellKnownUrl), asked, environment.name)
        }
    }

    @Test
    fun `refuses no scopes`() {
        assertFailsWith<IllegalArgumentException> { providerAsking(emptyList()) }
    }

    @Test
    fun `reuses the token until a minute before it expires`() = runBlocking {
        provider.accessToken()
        now = now.plusSeconds(59)
        provider.accessToken()

        assertEquals(1, tokenRequests.size)
    }

    @Test
    fun `waits for a new token when the current one has 10 seconds left`() = runBlocking {
        provider.accessToken()
        tokenAnswer = tokenResponse("token-2")
        now = now.plusSeconds(110)

        assertEquals("token-2", provider.accessToken().value)
    }

    @Test
    fun `in the last minute, calls get the current token while the next one is fetched`() = runWithTimeout {
        provider.accessToken()
        holdTokenRequests = true
        now = now.plusSeconds(60)
        assertEquals("token-1", provider.accessToken().value)
        now = now.plusSeconds(49)
        assertEquals("token-1", provider.accessToken().value)

        heldTokenRequests.receive().complete(tokenResponse("token-2"))
        backgroundFetchesFinish()

        assertEquals("token-2", provider.accessToken().value)
        assertEquals(2, tokenRequests.size)
    }

    @Test
    fun `a failed fetch in the last minute keeps the current token, and the next call tries again`() = runWithTimeout {
        provider.accessToken()
        holdTokenRequests = true
        now = now.plusSeconds(60)
        provider.accessToken()
        heldTokenRequests.receive().complete(maskinportenDown)
        backgroundFetchesFinish()

        assertEquals("token-1", provider.accessToken().value)
        heldTokenRequests.receive().complete(tokenResponse("token-2"))
        backgroundFetchesFinish()

        assertEquals("token-2", provider.accessToken().value)
    }

    @Test
    fun `fetches Maskinporten's metadata only once`() = runBlocking {
        provider.accessToken()
        now = now.plusSeconds(120)
        provider.accessToken()

        assertEquals(2, tokenRequests.size)
        assertEquals(1, metadataRequests)
    }

    @Test
    fun `calls at the same time share one token request`() = runBlocking {
        val tokens = List(2) { async { provider.accessToken() } }.awaitAll()

        assertEquals(listOf("token-1", "token-1"), tokens.map { it.value })
        assertEquals(1, tokenRequests.size)
    }

    @Test
    fun `calls that wait share one fetch, also when it fails, and the next call fetches again`() = runWithTimeout {
        holdTokenRequests = true
        val calls = List(2) { async(start = CoroutineStart.UNDISPATCHED) { runCatching { provider.accessToken() } } }
        heldTokenRequests.receive().complete(maskinportenDown)

        calls.awaitAll().forEach { assertIs<DigdirException>(it.exceptionOrNull()) }
        assertEquals(1, tokenRequests.size)

        holdTokenRequests = false
        assertEquals("token-1", provider.accessToken().value)
        assertEquals(2, tokenRequests.size)
    }

    @Test
    fun `a cancelled call does not stop the fetch for the calls waiting with it`() = runWithTimeout {
        holdTokenRequests = true
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) { provider.accessToken() }
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { provider.accessToken() }
        val held = heldTokenRequests.receive()

        cancelled.cancel()
        held.complete(tokenAnswer)

        assertEquals("token-1", waiting.await().value)
    }

    @Test
    fun `close stops a running fetch`() = runWithTimeout {
        holdTokenRequests = true
        val call = async(start = CoroutineStart.UNDISPATCHED) { runCatching { provider.accessToken() } }
        heldTokenRequests.receive()

        provider.close()

        assertIs<CancellationException>(call.await().exceptionOrNull())
    }

    @Test
    fun `a refused token is replaced, and refusing it again keeps its replacement`() = runBlocking {
        provider.accessToken()
        provider.refused(AccessToken.of("token-1"))
        tokenAnswer = tokenResponse("token-2")
        assertEquals("token-2", provider.accessToken().value)

        provider.refused(AccessToken.of("token-1"))

        assertEquals("token-2", provider.accessToken().value)
        assertEquals(2, tokenRequests.size)
    }

    @Test
    fun `an answer without the expected JSON is a DigdirException that does not quote it`() {
        tokenAnswer = OutgoingResponse(200, "application/json", """{"access_token":"secret-token"}""".toByteArray())

        val e = assertFailsWith<DigdirException> { runBlocking { provider.accessToken() } }

        // Coroutines may rethrow a copy with the original as its cause, so check the whole chain
        val chain = generateSequence<Throwable>(e) { it.cause }.toList()
        assertTrue(chain.none { "secret-token" in it.message.orEmpty() || it is SerializationException })
    }

    @Test
    fun `a refused grant names Maskinporten's answer`() {
        tokenAnswer = OutgoingResponse(400, "application/json", """{"error":"invalid_grant"}""".toByteArray())

        val e = assertFailsWith<DigdirException> { runBlocking { provider.accessToken() } }

        assertContains(e.message!!, "HTTP 400")
        assertContains(e.message!!, "invalid_grant")
    }

    private fun tokenResponse(token: String) =
        OutgoingResponse(200, "application/json", """{"access_token":"$token","expires_in":120}""".toByteArray())

    private val maskinportenDown = OutgoingResponse(503, "text/plain", "Down".toByteArray())

    // A call that waits when it should not would hang the test, so fail it instead
    private fun runWithTimeout(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(5.seconds) { block() } }

    // The provider's scope is a child of fetches, and each fetch is a child of that scope
    private suspend fun backgroundFetchesFinish() = fetches.children.flatMap { it.children }.forEach { it.join() }

    private fun decodeJson(part: String): JsonObject =
        Json.parseToJsonElement(Base64.getUrlDecoder().decode(part).decodeToString()).jsonObject
}
