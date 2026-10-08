package no.kartverket.maskinportenmanagement.restserver

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.kartverket.maskinportenmanagement.client.auth.JwtGrantSigner
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64

/**
 * Signs with a Cloud KMS key of algorithm RSA_SIGN_PKCS1_*_SHA256, whose signatures are RS256 as they are. The private
 * key never leaves KMS: we send the SHA-256 digest and get the signature back.
 */
internal class KmsJwtGrantSigner(
    private val keyVersion: String,
    private val kmsUrl: String,
    private val credentials: GoogleCredentials,
    private val httpClient: HttpClient,
) : JwtGrantSigner {
    override suspend fun sign(signingInput: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(signingInput)
        val request = HttpRequest.newBuilder(URI.create("${kmsUrl.trimEnd('/')}/v1/$keyVersion:asymmetricSign"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${accessToken()}")
            .POST(HttpRequest.BodyPublishers.ofString("""{"digest":{"sha256":"${Base64.getEncoder().encodeToString(digest)}"}}"""))

        val response = httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).await()
        check(response.statusCode() == 200) {
            "Cloud KMS did not sign with $keyVersion: HTTP ${response.statusCode()}: ${response.body()}"
        }
        return Base64.getDecoder().decode(json.decodeFromString<SignResponse>(response.body()).signature)
    }

    private suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        credentials.refreshIfExpired()
        credentials.accessToken!!.tokenValue
    }

    @Serializable
    private class SignResponse(val signature: String)

    companion object {
        const val GOOGLE_KMS_URL = "https://cloudkms.googleapis.com"

        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val json = Json { ignoreUnknownKeys = true }
    }
}
