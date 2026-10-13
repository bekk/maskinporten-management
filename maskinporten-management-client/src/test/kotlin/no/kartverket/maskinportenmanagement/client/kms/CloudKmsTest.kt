package no.kartverket.maskinportenmanagement.client.kms

import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.kms.v1.AsymmetricSignRequest
import com.google.cloud.kms.v1.AsymmetricSignResponse
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm
import com.google.cloud.kms.v1.GetPublicKeyRequest
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.kms.v1.KeyManagementServiceGrpc
import com.google.cloud.kms.v1.KeyManagementServiceSettings
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Signature
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CloudKmsTest {

    private val keyPair = rsaKeyPair()
    private val keyName = "projects/p/locations/europe-north1/keyRings/maskinporten/cryptoKeys/jwt-grant/cryptoKeyVersions/1"
    private val servers = mutableListOf<Server>()
    private val channels = mutableListOf<ManagedChannel>()

    @AfterTest
    fun stop() {
        channels.forEach { it.shutdownNow() }
        servers.forEach { it.shutdownNow() }
    }

    @Test
    fun `asks KMS to sign a SHA-256 digest of the data with the key`() {
        val fake = FakeKms(keyPair, CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256)
        val data = "header.payload".toByteArray()

        val signature = CloudKms(keyName, client(fake)).use { it.sign(data) }

        assertEquals(keyName, fake.signRequests.single().name)
        assertContentEquals(MessageDigest.getInstance("SHA-256").digest(data), fake.signRequests.single().digest.sha256.toByteArray())
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(keyPair.public)
            update(data)
        }
        assertTrue(verifier.verify(signature))
    }

    @Test
    fun `reads the public key from KMS`() {
        val fake = FakeKms(keyPair, CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_3072_SHA256)

        val publicKey = CloudKms(keyName, client(fake)).use { it.publicKey }

        assertContentEquals(keyPair.public.encoded, publicKey.encoded)
    }

    @Test
    fun `refuses a key that does not sign with RS256`() {
        for (algorithm in listOf(CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_4096_SHA512, CryptoKeyVersionAlgorithm.RSA_SIGN_PSS_2048_SHA256)) {
            val fake = FakeKms(keyPair, algorithm)

            val e = assertFailsWith<IllegalArgumentException>(algorithm.name) { CloudKms(keyName, client(fake)) }

            assertContains(e.message!!, algorithm.name)
        }
    }

    @Test
    fun `refuses a name that is not a key version`() {
        val fake = FakeKms(keyPair, CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256)

        for (name in listOf("projects/p/locations/europe-north1/keyRings/maskinporten/cryptoKeys/jwt-grant", "")) {
            assertFailsWith<IllegalArgumentException>(name) { CloudKms(name, client(fake)) }
        }
    }

    private fun client(fake: FakeKms): KeyManagementServiceClient {
        val name = InProcessServerBuilder.generateName()
        servers += InProcessServerBuilder.forName(name).directExecutor().addService(fake).build().start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build().also { channels += it }
        return KeyManagementServiceClient.create(
            KeyManagementServiceSettings.newBuilder()
                .setTransportChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build(),
        )
    }
}

// Like KMS, it signs a digest it is given, so it needs the DigestInfo that SHA256withRSA would have added
private class FakeKms(
    private val keyPair: KeyPair,
    private val algorithm: CryptoKeyVersionAlgorithm,
) : KeyManagementServiceGrpc.KeyManagementServiceImplBase() {
    val signRequests = mutableListOf<AsymmetricSignRequest>()

    override fun getPublicKey(request: GetPublicKeyRequest, response: StreamObserver<com.google.cloud.kms.v1.PublicKey>) {
        response.onNext(
            com.google.cloud.kms.v1.PublicKey.newBuilder()
                .setName(request.name)
                .setAlgorithm(algorithm)
                .setPem(pem("PUBLIC KEY", keyPair.public.encoded))
                .build(),
        )
        response.onCompleted()
    }

    override fun asymmetricSign(request: AsymmetricSignRequest, response: StreamObserver<AsymmetricSignResponse>) {
        signRequests += request
        val signature = Signature.getInstance("NONEwithRSA").run {
            initSign(keyPair.private)
            update(SHA256_DIGEST_INFO + request.digest.sha256.toByteArray())
            sign()
        }
        response.onNext(AsymmetricSignResponse.newBuilder().setName(request.name).setSignature(ByteString.copyFrom(signature)).build())
        response.onCompleted()
    }

    private companion object {
        val SHA256_DIGEST_INFO = java.util.HexFormat.of().parseHex("3031300d060960864801650304020105000420")
    }
}
