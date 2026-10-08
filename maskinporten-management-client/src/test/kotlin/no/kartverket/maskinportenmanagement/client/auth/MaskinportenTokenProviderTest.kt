package no.kartverket.maskinportenmanagement.client.auth

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import no.kartverket.maskinportenmanagement.client.DigdirException
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpClient
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpRequest
import no.kartverket.maskinportenmanagement.client.http.DigdirHttpResponse
import java.net.URI
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MaskinportenTokenProviderTest {

    private val certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(resource("test-certificate.pem").byteInputStream()) as X509Certificate

    private val signer = JwtGrantSigner { input ->
        val der = Base64.getMimeDecoder().decode(resource("test-key.pem").lines().filterNot { it.startsWith("-----") }.joinToString(""))
        Signature.getInstance("SHA256withRSA").run {
            initSign(KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der)))
            update(input)
            sign()
        }
    }

    private var now = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private val tokenRequests = mutableListOf<DigdirHttpRequest>()
    private var tokenAnswer = DigdirHttpResponse(200, "application/json", """{"access_token":"token-1","expires_in":120}""".toByteArray())

    private val maskinporten = DigdirHttpClient { request ->
        when (request.url.toString()) {
            "https://maskinporten.test/.well-known/oauth-authorization-server" -> DigdirHttpResponse(
                200,
                "application/json",
                """{"issuer":"https://maskinporten.test/","token_endpoint":"https://maskinporten.test/token","jwks_uri":"x"}""".toByteArray(),
            )

            "https://maskinporten.test/token" -> tokenAnswer.also { tokenRequests += request }

            else -> error("Unexpected request to ${request.url}")
        }
    }

    private val provider = MaskinportenTokenProvider(
        wellKnownUrl = URI("https://maskinporten.test/.well-known/oauth-authorization-server"),
        clientId = "test-client",
        scopes = "idporten:scopes.write",
        certificateChain = listOf(certificate),
        signer = signer,
        httpClient = maskinporten,
        clock = clock,
    )

    @Test
    fun `sends a JWT grant signed with the business certificate's key`() = runBlocking {
        assertEquals("token-1", provider.accessToken())

        val request = tokenRequests.single()
        assertEquals("POST", request.method)
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        val form = request.body!!.decodeToString().split('&').associate {
            val (key, value) = it.split('=', limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }
        assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", form["grant_type"])

        val (header, claims, signature) = form.getValue("assertion").split('.')
        val headerJson = decodeJson(header)
        assertEquals("RS256", headerJson.getValue("alg").jsonPrimitive.content)
        val x5c = headerJson.getValue("x5c").jsonArray.single().jsonPrimitive.content
        assertTrue(Base64.getDecoder().decode(x5c).contentEquals(certificate.encoded))

        val claimsJson = decodeJson(claims)
        assertEquals("https://maskinporten.test/", claimsJson.getValue("aud").jsonPrimitive.content)
        assertEquals("test-client", claimsJson.getValue("iss").jsonPrimitive.content)
        assertEquals("idporten:scopes.write", claimsJson.getValue("scope").jsonPrimitive.content)
        assertEquals(now.epochSecond, claimsJson.getValue("iat").jsonPrimitive.long)
        assertEquals(now.epochSecond + 60, claimsJson.getValue("exp").jsonPrimitive.long)
        assertTrue(claimsJson.getValue("jti").jsonPrimitive.content.isNotBlank())

        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(certificate.publicKey)
            update("$header.$claims".toByteArray())
        }
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(signature)))
    }

    @Test
    fun `reuses the token until shortly before it expires`() = runBlocking {
        provider.accessToken()
        now = now.plusSeconds(99)
        provider.accessToken()
        assertEquals(1, tokenRequests.size)

        tokenAnswer = DigdirHttpResponse(200, "application/json", """{"access_token":"token-2","expires_in":120}""".toByteArray())
        now = now.plusSeconds(1)
        assertEquals("token-2", provider.accessToken())
        assertEquals(2, tokenRequests.size)
    }

    @Test
    fun `a refused grant names Maskinporten's answer`() {
        tokenAnswer = DigdirHttpResponse(400, "application/json", """{"error":"invalid_grant"}""".toByteArray())

        val e = assertFailsWith<DigdirException> { runBlocking { provider.accessToken() } }

        assertContains(e.message!!, "HTTP 400")
        assertContains(e.message!!, "invalid_grant")
    }

    private fun decodeJson(part: String): JsonObject =
        Json.parseToJsonElement(Base64.getUrlDecoder().decode(part).decodeToString()).jsonObject

    private fun resource(name: String): String = javaClass.classLoader.getResource(name)!!.readText()
}
