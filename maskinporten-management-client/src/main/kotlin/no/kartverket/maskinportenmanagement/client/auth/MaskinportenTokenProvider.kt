package no.kartverket.maskinportenmanagement.client.auth

import com.nimbusds.jwt.JWTClaimsSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.client.Scope
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import no.kartverket.maskinportenmanagement.client.http.OutgoingHttpClient
import no.kartverket.maskinportenmanagement.client.http.OutgoingRequest
import no.kartverket.maskinportenmanagement.client.http.OutgoingResponse
import no.kartverket.maskinportenmanagement.client.kms.GrantSigner
import no.kartverket.maskinportenmanagement.client.kms.Kms
import java.net.URI
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * Gets access tokens from Maskinporten with a JWT grant that [kms] signs and that carries the business certificate
 * chain in `x5c`, and reuses each token until shortly before it expires. It fetches the next token in the background
 * while the current one still works, so most calls never wait for Maskinporten.
 *
 * [certificateChainPem] is the chain in PEM, the business certificate for the KMS key first.
 */
public class MaskinportenTokenProvider(
    private val wellKnownUrl: URI,
    private val clientId: String,
    private val scopes: List<Scope>,
    kms: Kms,
    certificateChainPem: String,
    private val httpClient: OutgoingHttpClient,
    private val clock: Clock = Clock.systemUTC(),
    context: CoroutineContext = Dispatchers.Default,
) : AccessTokenProvider,
    AutoCloseable {
    init {
        require(scopes.isNotEmpty()) { "scopes must not be empty" }
    }

    private val grantSigner = GrantSigner(kms, certificateChainPem)

    private val scope = CoroutineScope(context + SupervisorJob(context[Job]))
    private val mutex = Mutex()

    private var metadata: Metadata? = null

    private var token: Token? = null
    private var inFlight: Deferred<AccessToken>? = null

    override suspend fun accessToken(): AccessToken {
        val fetch = mutex.withLock {
            val now = clock.instant()
            val current = token
            if (current != null && now < current.refreshAt) return current.value
            val fetch = inFlight ?: scope.async { fetchToken() }.also { inFlight = it }
            if (current != null && now < current.expiresAt) return current.value
            fetch
        }
        return fetch.await()
    }

    override fun close(): Unit = scope.cancel()

    override suspend fun refused(token: AccessToken): Unit = mutex.withLock {
        if (this.token?.value == token) this.token = null
    }

    private suspend fun fetchToken(): AccessToken {
        var fetched: Token? = null
        try {
            return requestToken().also { fetched = it }.value
        } finally {
            mutex.withLock {
                fetched?.let { token = it }
                inFlight = null
            }
        }
    }

    private suspend fun requestToken(): Token {
        val metadata = metadata ?: fetchMetadata().also { metadata = it }
        val requestedAt = clock.instant()
        val form = "grant_type=${encode(GRANT_TYPE)}&assertion=${encode(grant(metadata.issuer, requestedAt))}"
        val response = httpClient.send(
            OutgoingRequest(
                method = HttpMethod.POST,
                url = URI.create(metadata.tokenEndpoint),
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded", "Accept" to "application/json"),
                body = form.toByteArray(),
            ),
        )
        val tokenResponse = response.decode<TokenResponse>("token request")
        val expiry = requestedAt.plusSeconds(tokenResponse.expiresIn)
        return Token(
            value = AccessToken.of(tokenResponse.accessToken),
            refreshAt = expiry.minusSeconds(REFRESH_MARGIN_SECONDS),
            expiresAt = expiry.minusSeconds(EXPIRY_MARGIN_SECONDS),
        )
    }

    private suspend fun fetchMetadata(): Metadata = httpClient.send(
        OutgoingRequest(method = HttpMethod.GET, url = wellKnownUrl, headers = mapOf("Accept" to "application/json")),
    ).decode("metadata request")

    private suspend fun grant(audience: String, now: Instant): String {
        val claims = JWTClaimsSet.Builder()
            .audience(audience)
            .issuer(clientId)
            .claim("scope", scopes.joinToString(" ") { it.value })
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(GRANT_LIFETIME_SECONDS)))
            .jwtID(UUID.randomUUID().toString())
            .build()
        return withContext(Dispatchers.IO) { grantSigner.sign(claims) }
    }

    private inline fun <reified T> OutgoingResponse.decode(what: String): T {
        val text = body.decodeToString()
        if (statusCode != 200) throw DigdirException("Maskinporten $what failed with HTTP $statusCode: $text")
        return try {
            json.decodeFromString(text)
        } catch (e: SerializationException) {
            // Neither the text nor e, whose message quotes it: a token response may hold the token
            throw DigdirException("Maskinporten $what answered HTTP 200 without the expected JSON")
        }
    }

    @Serializable
    private class Metadata(
        val issuer: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
    )

    @Serializable
    private class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
    )

    private class Token(val value: AccessToken, val refreshAt: Instant, val expiresAt: Instant)

    private companion object {
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"

        // Digdir's recommended lifetime
        const val GRANT_LIFETIME_SECONDS = 120L

        // From here, calls get the current token while the next one is fetched in the background
        const val REFRESH_MARGIN_SECONDS = 60L

        // So a token does not run out on its way to Digdir
        const val EXPIRY_MARGIN_SECONDS = 10L

        val json = Json { ignoreUnknownKeys = true }

        fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
    }
}
