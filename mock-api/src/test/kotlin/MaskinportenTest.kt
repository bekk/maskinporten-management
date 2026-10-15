package com.maskinporten

import com.maskinporten.maskinporten.MaskinportenMock
import com.maskinporten.maskinporten.maskinportenMockFromConfig
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaskinportenTest {

    private val issuer = "http://localhost/"

    // Test keys and certificates, made fresh for each test so no private key is checked in
    private val ca = rsaKeyPair()
    private val caCertificate = certificate("CN=Fake CA", ca.public, "CN=Fake CA", ca.private)
    private val businessKey = rsaKeyPair()
    private val businessCertificate = certificate(
        "CN=STATENS KARTVERK TEST, SERIALNUMBER=971040238, organizationIdentifier=NTRNO-971040238, O=STATENS KARTVERK, C=NO",
        businessKey.public, "CN=Fake CA", ca.private,
    )
    private val skatteetatenKey = rsaKeyPair()
    private val skatteetatenCertificate = certificate(
        "CN=SKATTEETATEN TEST, SERIALNUMBER=974761076, organizationIdentifier=NTRNO-974761076, O=SKATTEETATEN, C=NO",
        skatteetatenKey.public, "CN=Fake CA", ca.private,
    )
    private val untrustedKey = rsaKeyPair()
    private val untrustedCertificate = certificate("CN=Untrusted", untrustedKey.public, "CN=Untrusted", untrustedKey.private)

    private val caFile = createTempFile(suffix = ".crt").apply {
        writeText("-----BEGIN CERTIFICATE-----\n${Base64.getMimeEncoder().encodeToString(caCertificate.encoded)}\n-----END CERTIFICATE-----\n")
        toFile().deleteOnExit()
    }

    private fun maskinportenTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApp(
        maskinporten = mapOf(
            "maskinporten.enabled" to "true",
            "maskinporten.issuer" to issuer,
            "maskinporten.caFile" to caFile.toString(),
        ),
        block = block,
    )

    private fun grant(
        chain: List<X509Certificate> = listOf(businessCertificate),
        key: PrivateKey = businessKey.private,
        aud: String = issuer,
        issuedAt: Instant? = Instant.now(),
        expiresAt: Instant = (issuedAt ?: Instant.now()).plusSeconds(120),
        iss: String? = "test-client",
        scope: String? = "idporten:scopes.write",
        jti: String? = UUID.randomUUID().toString(),
        alg: String = "RS256",
    ): String {
        val header = buildJsonObject {
            put("alg", alg)
            putJsonArray("x5c") { chain.forEach { add(Base64.getEncoder().encodeToString(it.encoded)) } }
        }
        val claims = buildJsonObject {
            iss?.let { put("iss", it) }
            put("aud", aud)
            scope?.let { put("scope", it) }
            issuedAt?.let { put("iat", it.epochSecond) }
            put("exp", expiresAt.epochSecond)
            jti?.let { put("jti", it) }
        }
        val signingInput = "${base64Url(header.toString().toByteArray())}.${base64Url(claims.toString().toByteArray())}"
        val signature = Signature.getInstance("SHA${alg.removePrefix("RS")}withRSA").run {
            initSign(key)
            update(signingInput.toByteArray())
            sign()
        }
        return "$signingInput.${base64Url(signature)}"
    }

    private suspend fun ApplicationTestBuilder.requestToken(assertion: String): HttpResponse =
        client.submitForm(
            "/token",
            parameters {
                append("grant_type", GRANT_TYPE)
                append("assertion", assertion)
            },
        )

    private suspend fun ApplicationTestBuilder.token(scope: String = "idporten:scopes.write"): String =
        requestToken(grant(scope = scope)).json()["access_token"]!!.jsonPrimitive.content

    private suspend fun ApplicationTestBuilder.skatteetatenToken(): String =
        requestToken(grant(chain = listOf(skatteetatenCertificate), key = skatteetatenKey.private)).json()["access_token"]!!.jsonPrimitive.content

    private suspend fun ApplicationTestBuilder.createScope(token: String, prefix: String): HttpResponse =
        client.post("/api/v1/scopes") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "prefix": "$prefix",
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
                """.trimIndent(),
            )
        }

    private suspend fun ApplicationTestBuilder.updateScope(token: String, scope: String, body: String): HttpResponse =
        client.put("/api/v1/scopes?scope=$scope") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.assertRefused(assertion: String) {
        val response = requestToken(assertion)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_grant", response.json()["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `publishes its issuer and token endpoint`() = maskinportenTest {
        val metadata = client.get("/.well-known/oauth-authorization-server").json()

        assertEquals(issuer, metadata["issuer"]?.jsonPrimitive?.content)
        assertEquals("${issuer}token", metadata["token_endpoint"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a valid grant gets a token`() = maskinportenTest {
        val response = requestToken(grant())
        val body = response.json()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body["access_token"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("Bearer", body["token_type"]?.jsonPrimitive?.content)
        assertEquals("idporten:scopes.write", body["scope"]?.jsonPrimitive?.content)
    }

    @Test
    fun `refuses a certificate from a CA it does not trust`() = maskinportenTest {
        assertRefused(grant(chain = listOf(untrustedCertificate), key = untrustedKey.private))
    }

    @Test
    fun `refuses a grant signed with another key than the certificate's`() = maskinportenTest {
        assertRefused(grant(key = untrustedKey.private))
    }

    @Test
    fun `accepts RS384 and RS512 too`() = maskinportenTest {
        assertEquals(HttpStatusCode.OK, requestToken(grant(alg = "RS384")).status)
        assertEquals(HttpStatusCode.OK, requestToken(grant(alg = "RS512")).status)
    }

    @Test
    fun `refuses a grant for another audience, or that has expired`() = maskinportenTest {
        assertRefused(grant(aud = "https://maskinporten.no/"))
        assertRefused(grant(expiresAt = Instant.now().minusSeconds(1)))
    }

    @Test
    fun `refuses a grant without iss, scope or iat`() = maskinportenTest {
        assertRefused(grant(iss = null))
        assertRefused(grant(scope = null))
        assertRefused(grant(issuedAt = null, expiresAt = Instant.now().plusSeconds(60)))
    }

    @Test
    fun `refuses a grant whose iat is off by more than 10 seconds, or that lasts more than 180 seconds`() = maskinportenTest {
        assertRefused(grant(issuedAt = Instant.now().minusSeconds(30)))
        assertRefused(grant(issuedAt = Instant.now().plusSeconds(30)))
        assertRefused(grant(expiresAt = Instant.now().plusSeconds(300)))
        assertEquals(HttpStatusCode.OK, requestToken(grant(expiresAt = Instant.now().plusSeconds(180))).status)
    }

    @Test
    fun `refuses a grant that has been used before, with or without jti`() = maskinportenTest {
        val assertion = grant()
        assertEquals(HttpStatusCode.OK, requestToken(assertion).status)
        assertRefused(assertion)

        val withoutJti = grant(jti = null)
        assertEquals(HttpStatusCode.OK, requestToken(withoutJti).status)
        assertRefused(withoutJti)
    }

    @Test
    fun `a refused grant can be sent again once it is fixed`() = maskinportenTest {
        assertRefused(grant(aud = "https://maskinporten.no/", jti = "same-jti"))

        assertEquals(HttpStatusCode.OK, requestToken(grant(jti = "same-jti")).status)
    }

    @Test
    fun `the API needs a token it issued`() = maskinportenTest {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/scopes").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/scopes") { bearerAuth("made-up") }.status)
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/scopes") { bearerAuth(token()) }.status)
    }

    @Test
    fun `reading needs idporten scopes read or write, and changing needs write`() = maskinportenTest {
        val change = "/api/v1/scopes/access/991825827?scope=kartverk:grunnbok.read"
        val read = token("idporten:scopes.read")

        assertEquals(HttpStatusCode.OK, client.get("/api/v1/scopes") { bearerAuth(read) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/scopes") { bearerAuth(token("kartverk:matrikkel.read")) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put(change) { bearerAuth(read) }.status)
        assertEquals(HttpStatusCode.OK, client.put(change) { bearerAuth(token()) }.status)
    }

    @Test
    fun `the endpoints Digdir leaves open need no token`() = maskinportenTest {
        for (path in listOf("/api/v1/scopes/all", "/api/v1/prefix/all", "/api/v1/apis", "/api/v1/delegationsources", "/api/v1/integrations")) {
            assertEquals(HttpStatusCode.OK, client.get(path).status, path)
        }
    }

    @Test
    fun `lists show only the data of the organisation in the token`() = maskinportenTest {
        val skatteetaten = skatteetatenToken()

        assertEquals(listOf("skatt"), client.get("/api/v1/prefix") { bearerAuth(skatteetaten) }.jsonObjects().map { it.string("prefix") })
        assertTrue(client.get("/api/v1/scopes") { bearerAuth(skatteetaten) }.jsonObjects().isEmpty())
        assertTrue(client.get("/api/v1/scopes/access?consumer_orgno=971032081") { bearerAuth(skatteetaten) }.jsonObjects().isEmpty())
        val given = client.get("/api/v1/scopes/access/all") { bearerAuth(skatteetaten) }.jsonObjects()
        assertTrue(given.isNotEmpty() && given.all { it.string("consumer_orgno") == "974761076" })
    }

    @Test
    fun `another organisation's scopes are refused`() = maskinportenTest {
        val skatteetaten = skatteetatenToken()

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/scopes/scope?scope=kartverk:matrikkel.read") { bearerAuth(skatteetaten) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/api/v1/scopes?scope=kartverk:matrikkel.read") { bearerAuth(skatteetaten) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/v1/scopes/access/991825827?scope=kartverk:grunnbok.read") { bearerAuth(skatteetaten) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/scopes/access/changelog?scopeName=kartverk:matrikkel.read") { bearerAuth(skatteetaten) }.status)
        assertEquals(HttpStatusCode.Forbidden, createScope(skatteetaten, prefix = "kartverk").status)
    }

    @Test
    fun `a new scope belongs to the organisation in the token`() = maskinportenTest {
        val created = createScope(token(), prefix = "kartverk").json()

        assertEquals("971040238", created["owner_orgno"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a new scope only gets a prefix the organisation owns`() = maskinportenTest {
        assertEquals(HttpStatusCode.Forbidden, createScope(token(), prefix = "not-registered").status)
        assertEquals(HttpStatusCode.Forbidden, createScope(token(), prefix = "skatt").status)
        assertEquals(HttpStatusCode.OK, createScope(token(), prefix = "kartverk-intern").status)
    }

    @Test
    fun `a changed scope still belongs to the organisation in the token`() = maskinportenTest {
        val updated = updateScope(token(), "kartverk:matrikkel.read", """{"owner_orgno": "974761076"}""").json()

        assertEquals("971040238", updated["owner_orgno"]?.jsonPrimitive?.content)
    }

    @Test
    fun `remembers the organisation and scopes of the tokens it issued`() {
        val maskinporten = maskinportenMock()
        val issued = maskinporten.issueToken(GRANT_TYPE, grant())

        val token = maskinporten.issuedToken(issued.token)
        assertEquals("971040238", token?.organizationNumber)
        assertEquals("idporten:scopes.write", token?.scope)
        assertNull(maskinporten.issuedToken("made-up"))
    }

    @Test
    fun `the issuer ends with a slash, so the token endpoint is right`() {
        assertEquals("http://localhost:8090/", MaskinportenMock("http://localhost:8090", caCertificate).issuer)
    }

    @Test
    fun `without a CA certificate it is off, unless it is turned on`() {
        fun config(vararg settings: Pair<String, String>) =
            MapApplicationConfig("maskinporten.caFile" to "does-not-exist.crt", *settings)
        val log = LoggerFactory.getLogger(javaClass)

        assertNull(maskinportenMockFromConfig(config(), log))
        assertFailsWith<IllegalStateException> { maskinportenMockFromConfig(config("maskinporten.enabled" to "true"), log) }
        assertNotNull(maskinportenMockFromConfig(MapApplicationConfig("maskinporten.caFile" to caFile.toString()), log))
    }

    @Test
    fun `reads the organisation number from organizationIdentifier or serialNumber`() {
        fun organizationNumberIn(subject: String): String {
            val key = rsaKeyPair()
            val chain = listOf(certificate(subject, key.public, "CN=Fake CA", ca.private))
            return maskinportenMock().issueToken(GRANT_TYPE, grant(chain = chain, key = key.private)).organizationNumber
        }

        assertEquals("971040238", organizationNumberIn("CN=Ny, organizationIdentifier=NTRNO-971040238, O=STATENS KARTVERK"))
        assertEquals("971040238", organizationNumberIn("CN=Gammel, SERIALNUMBER=971040238, O=STATENS KARTVERK"))
        assertFailsWith<Exception> { organizationNumberIn("CN=Uten organisasjonsnummer, O=STATENS KARTVERK") }
    }

    private fun maskinportenMock() = MaskinportenMock(issuer, caCertificate)

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun rsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun certificate(subject: String, publicKey: PublicKey, issuer: String, issuerKey: PrivateKey): X509Certificate {
        val now = Instant.now()
        val builder = JcaX509v3CertificateBuilder(
            X500Name(issuer),
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now.minus(Duration.ofMinutes(1))),
            Date.from(now.plus(Duration.ofDays(1))),
            X500Name(subject),
            publicKey,
        )
        return JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(issuerKey)))
    }

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    }
}
