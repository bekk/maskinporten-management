package no.kartverket.maskinportenmanagement.restserver

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KmsJwtGrantSignerTest {

    private val keyVersion = "projects/p/locations/global/keyRings/r/cryptoKeys/k/cryptoKeyVersions/1"
    private val requests = mutableListOf<Triple<String, String?, String>>()
    private var status = 200
    private val signature = byteArrayOf(1, 2, 3)

    private val kms = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/") { exchange ->
            requests += Triple(
                exchange.requestURI.path,
                exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestBody.readBytes().decodeToString(),
            )
            val body = if (status == 200) {
                """{"name":"$keyVersion","signature":"${Base64.getEncoder().encodeToString(signature)}"}"""
            } else {
                """{"error":{"message":"Permission denied"}}"""
            }
            exchange.sendResponseHeaders(status, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        start()
    }

    private val credentials = GoogleCredentials.create(AccessToken("workload-identity-token", null))
    private val signer = KmsJwtGrantSigner(keyVersion, "http://localhost:${kms.address.port}", credentials, HttpClient.newHttpClient())

    @AfterTest
    fun stop() = kms.stop(0)

    @Test
    fun `sends the SHA-256 digest to the key version with credentials and returns the signature`() = runBlocking {
        val input = "header.claims".toByteArray()

        assertContentEquals(signature, signer.sign(input))

        val (path, authorization, body) = requests.single()
        assertEquals("/v1/$keyVersion:asymmetricSign", path)
        assertEquals("Bearer workload-identity-token", authorization)
        val digest = Json.parseToJsonElement(body).jsonObject.getValue("digest").jsonObject.getValue("sha256").jsonPrimitive.content
        assertContentEquals(MessageDigest.getInstance("SHA-256").digest(input), Base64.getDecoder().decode(digest))
    }

    @Test
    fun `a refusal names the key and KMS's answer`() {
        status = 403

        val e = assertFailsWith<IllegalStateException> { runBlocking { signer.sign(byteArrayOf()) } }

        assertContains(e.message!!, keyVersion)
        assertContains(e.message!!, "Permission denied")
    }
}
