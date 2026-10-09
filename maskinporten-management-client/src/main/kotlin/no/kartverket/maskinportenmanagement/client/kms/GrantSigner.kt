package no.kartverket.maskinportenmanagement.client.kms

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.jca.JCAContext
import com.nimbusds.jose.util.Base64
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.cert.CertificateFactory

internal class GrantSigner(private val kms: Kms, certificateChainPem: String) {
    private val certificateChain: List<Base64>

    init {
        val certificates = CertificateFactory.getInstance("X.509").generateCertificates(certificateChainPem.byteInputStream())
        require(certificates.isNotEmpty()) { "The certificate chain has no certificates" }
        require(certificates.first().publicKey.encoded.contentEquals(kms.publicKey.encoded)) {
            "The first certificate in the chain is not for the KMS key"
        }
        certificateChain = certificates.map { Base64.encode(it.encoded) }
    }

    fun sign(claims: JWTClaimsSet): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).x509CertChain(certificateChain).build()
        return SignedJWT(header, claims).apply { sign(KmsSigner(kms)) }.serialize()
    }
}

private class KmsSigner(private val kms: Kms) : JWSSigner {
    override fun sign(header: JWSHeader, signingInput: ByteArray): Base64URL = Base64URL.encode(kms.sign(signingInput))

    override fun supportedJWSAlgorithms(): Set<JWSAlgorithm> = setOf(JWSAlgorithm.RS256)

    override fun getJCAContext(): JCAContext = JCAContext()
}
