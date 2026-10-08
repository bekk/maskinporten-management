package no.kartverket.maskinportenmanagement.client.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpClient
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpRequest
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import java.net.URI
import java.net.URLEncoder
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Gets access tokens from Maskinporten with a JWT grant that carries the business certificate in `x5c`, and reuses
 * each token until shortly before it expires.
 */
public class MaskinportenTokenProvider(
    private val wellKnownUrl: URI,
    private val clientId: String,
    private val scopes: String,
    private val certificateChain: List<X509Certificate>,
    private val signer: JwtGrantSigner,
    private val httpClient: DigdirHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) : AccessTokenProvider {
    init {
        require(certificateChain.isNotEmpty()) { "certificateChain must hold at least the business certificate" }
    }

    private val mutex = Mutex()
    private var metadata: Metadata? = null
    private var token: Token? = null

    override suspend fun accessToken(): String = mutex.withLock {
        token?.takeIf { clock.instant() < it.refreshAt }?.let { return it.value }

        val metadata = metadata ?: fetchMetadata().also { metadata = it }
        val requestedAt = clock.instant()
        val form = "grant_type=${encode(GRANT_TYPE)}&assertion=${encode(grant(metadata.issuer, requestedAt))}"
        val response = httpClient.send(
            DigdirHttpRequest(
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

    private suspend fun fetchMetadata(): Metadata = httpClient.send(
        DigdirHttpRequest(method = "GET", url = wellKnownUrl, headers = mapOf("Accept" to "application/json")),
    ).decode("metadata request")

    private suspend fun grant(audience: String, now: Instant): String {
        val header = buildJsonObject {
            put("alg", "RS256")
            putJsonArray("x5c") { certificateChain.forEach { add(Base64.getEncoder().encodeToString(it.encoded)) } }
        }
        val claims = buildJsonObject {
            put("aud", audience)
            put("iss", clientId)
            put("scope", scopes)
            put("iat", now.epochSecond)
            put("exp", now.epochSecond + GRANT_LIFETIME_SECONDS)
            put("jti", UUID.randomUUID().toString())
        }
        val signingInput = "${base64Url(header.toString().toByteArray())}.${base64Url(claims.toString().toByteArray())}"
        return "$signingInput.${base64Url(signer.sign(signingInput.toByteArray()))}"
    }

    private inline fun <reified T> DigdirHttpResponse.decode(what: String): T {
        val text = body.decodeToString()
        if (statusCode != 200) throw DigdirException("Maskinporten $what failed with HTTP $statusCode: $text")
        return json.decodeFromString(text)
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

        // Maskinporten accepts grants that live at most 120 seconds
        const val GRANT_LIFETIME_SECONDS = 60L
        const val REFRESH_MARGIN_SECONDS = 20L

        val json = Json { ignoreUnknownKeys = true }

        fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
