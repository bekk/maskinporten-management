package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.auth.JwtGrantSigner
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Stands in for Cloud KMS locally: signs RS256 with an RSA private key from a PKCS#8 PEM file, such as the one
 * scripts/setup-local-kms.sh in maskinporten-management-frontend-mock writes.
 */
internal class LocalJwtGrantSigner(private val privateKey: PrivateKey) : JwtGrantSigner {
    override suspend fun sign(signingInput: ByteArray): ByteArray = Signature.getInstance("SHA256withRSA").run {
        initSign(privateKey)
        update(signingInput)
        sign()
    }

    companion object {
        fun fromPemFile(path: String): LocalJwtGrantSigner {
            val file = File(path)
            check(file.isFile) { "kms.localKeyFile must point to a PEM file, but was \"$path\" (see .env.example)" }
            val pem = file.readText()
            check("-----BEGIN PRIVATE KEY-----" in pem) { "$path must hold an RSA private key in PKCS#8 PEM" }
            val der = Base64.getMimeDecoder().decode(pem.lines().filterNot { it.startsWith("-----") }.joinToString(""))
            return LocalJwtGrantSigner(KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der)))
        }
    }
}
