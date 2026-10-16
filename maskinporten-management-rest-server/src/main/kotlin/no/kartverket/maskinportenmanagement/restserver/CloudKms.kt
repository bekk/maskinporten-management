package no.kartverket.maskinportenmanagement.restserver

import com.google.api.gax.rpc.ApiException
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_3072_SHA256
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_4096_SHA256
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.Digest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import no.kartverket.maskinportenmanagement.client.kms.Kms
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal class CloudKms(
    keyVersionName: String,
    private val client: KeyManagementServiceClient,
) : Kms,
    AutoCloseable {
    constructor(keyVersionName: String) : this(keyVersionName, KeyManagementServiceClient.create())

    private val name: CryptoKeyVersionName
    override val publicKey: PublicKey

    init {
        try {
            name = requireNotNull(CryptoKeyVersionName.parse(keyVersionName)) { "The KMS key version name is empty" }
            val key = client.getPublicKey(name)
            require(key.algorithm in RS256) {
                "The KMS key must sign with RS256 (RSA PKCS #1 with SHA-256), but $keyVersionName uses ${key.algorithm}"
            }
            publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pemContent(key.pem, "PUBLIC KEY")))
        } catch (e: Exception) {
            client.close()
            throw e
        }
    }

    override fun sign(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val response = try {
            client.asymmetricSign(name, Digest.newBuilder().setSha256(ByteString.copyFrom(digest)).build())
        } catch (e: ApiException) {
            throw CloudKmsException("Call to Cloud KMS failed: $e", e)
        }
        return response.signature.toByteArray()
    }

    override fun close(): Unit = client.close()

    private companion object {
        val RS256 = setOf(RSA_SIGN_PKCS1_2048_SHA256, RSA_SIGN_PKCS1_3072_SHA256, RSA_SIGN_PKCS1_4096_SHA256)
    }
}

internal class CloudKmsException(message: String, cause: Throwable) : RuntimeException(message, cause)

// A copy of the client's pemContent, which the client keeps internal so its API holds no PEM parser
private fun pemContent(pem: String, type: String): ByteArray {
    val body = pem.substringAfter("-----BEGIN $type-----", "").substringBefore("-----END $type-----", "")
    require(body.isNotBlank()) { "Expected a PEM block of type $type" }
    return Base64.getMimeDecoder().decode(body)
}
