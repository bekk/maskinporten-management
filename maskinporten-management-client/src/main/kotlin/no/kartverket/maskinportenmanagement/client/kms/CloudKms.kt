package no.kartverket.maskinportenmanagement.client.kms

import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_3072_SHA256
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_4096_SHA256
import com.google.cloud.kms.v1.CryptoKeyVersionName
import com.google.cloud.kms.v1.Digest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.protobuf.ByteString
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

public class CloudKms internal constructor(
    keyVersionName: String,
    private val client: KeyManagementServiceClient,
) : Kms,
    AutoCloseable {
    public constructor(keyVersionName: String) : this(keyVersionName, KeyManagementServiceClient.create())

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
        return client.asymmetricSign(name, Digest.newBuilder().setSha256(ByteString.copyFrom(digest)).build())
            .signature
            .toByteArray()
    }

    override fun close(): Unit = client.close()

    private companion object {
        val RS256 = setOf(RSA_SIGN_PKCS1_2048_SHA256, RSA_SIGN_PKCS1_3072_SHA256, RSA_SIGN_PKCS1_4096_SHA256)
    }
}
