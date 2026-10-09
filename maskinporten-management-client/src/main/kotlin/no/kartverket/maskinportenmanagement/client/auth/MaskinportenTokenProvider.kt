package no.kartverket.maskinportenmanagement.client.auth

import com.nimbusds.jwt.JWTClaimsSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.DigdirException
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

/**
 * Gets access tokens from Maskinporten with a JWT grant that [kms] signs and that carries the business certificate
 * chain in `x5c`, and reuses each token until shortly before it expires.
 *
 * [certificateChainPem] is the chain in PEM, the business certificate for the KMS key first.
 */
public class MaskinportenTokenProvider(
    private val wellKnownUrl: URI,
    private val clientId: String,
    private val scopes: String,
    kms: Kms,
    certificateChainPem: String,
    private val httpClient: OutgoingHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) : AccessTokenProvider {
    private val grantSigner = GrantSigner(kms, certificateChainPem)
    private val mutex = Mutex()
    private var metadata: Metadata? = null
    private var token: Token? = null

    override suspend fun accessToken(): String = mutex.withLock {
        token?.takeIf { clock.instant() < it.refreshAt }?.let { return it.value }

        val metadata = metadata ?: fetchMetadata().also { metadata = it }
        val requestedAt = clock.instant()
        val form = "grant_type=${encode(GRANT_TYPE)}&assertion=${encode(grant(metadata.issuer, requestedAt))}"
        val response = httpClient.send(
            OutgoingRequest(
                method = "POST",
                url = URI.create(metadata.tokenEndpoint),
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded", "Accept" to "application/json"),
                body = form.toByteArray(),
            ),
        )
        val tokenResponse = response.decode<TokenResponse>("token request")
        token = Token(tokenResponse.accessToken, requestedAt.plusSeconds(tokenResponse.expiresIn - REFRESH_MARGIN_SECONDS))
        tokenResponse.accessToken
    }

    override suspend fun refused(token: String): Unit = mutex.withLock {
        // Only forget it if no other call has fetched a new one since
        if (this.token?.value == token) this.token = null
    }

    private suspend fun fetchMetadata(): Metadata = httpClient.send(
        OutgoingRequest(method = "GET", url = wellKnownUrl, headers = mapOf("Accept" to "application/json")),
    ).decode("metadata request")

    private suspend fun grant(audience: String, now: Instant): String {
        val claims = JWTClaimsSet.Builder()
            .audience(audience)
            .issuer(clientId)
            .claim("scope", scopes)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(GRANT_LIFETIME_SECONDS)))
            .jwtID(UUID.randomUUID().toString())
            .build()
        // Cloud KMS signs over the network and blocks
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

    private class Token(val value: String, val refreshAt: Instant)

    private companion object {
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"

        // Maskinporten accepts grants that live at most 180 seconds, and recommends 120
        const val GRANT_LIFETIME_SECONDS = 60L
        const val REFRESH_MARGIN_SECONDS = 20L

        val json = Json { ignoreUnknownKeys = true }

        fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
    }
}
