package no.kartverket.maskinportenmanagement.client.kms

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

internal fun rsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

internal fun pem(type: String, der: ByteArray): String =
    "-----BEGIN $type-----\n${Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)}\n-----END $type-----\n"

internal fun pem(certificate: X509Certificate): String = pem("CERTIFICATE", certificate.encoded)

// A test certificate for the key, issued by a fake CA, and the CA's own certificate
internal fun certificateChain(publicKey: PublicKey): List<X509Certificate> {
    val ca = rsaKeyPair()
    val caCertificate = certificate("CN=Falsk CA", ca.public, "CN=Falsk CA", ca.private)
    val certificate = certificate("O=STATENS KARTVERK, CN=STATENS KARTVERK TEST", publicKey, "CN=Falsk CA", ca.private)
    return listOf(certificate, caCertificate)
}

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
