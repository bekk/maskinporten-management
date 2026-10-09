package no.kartverket.maskinportenmanagement.client.kms

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GrantSignerTest {

    private val keyPair = rsaKeyPair()
    private val kms = LocalKms(keyPair.private as RSAPrivateCrtKey)
    private val claims = JWTClaimsSet.Builder().issuer("client-id").audience("https://test.maskinporten.no/").build()

    @Test
    fun `signs the grant with RS256 and puts the whole certificate chain in x5c`() {
        val chain = certificateChain(keyPair.public)

        val grant = SignedJWT.parse(GrantSigner(kms, chain.joinToString("") { pem(it) }).sign(claims))

        assertEquals(JWSAlgorithm.RS256, grant.header.algorithm)
        assertEquals(chain.map { it.encoded.toList() }, grant.header.x509CertChain.map { it.decode().toList() })
        assertTrue(grant.verify(RSASSAVerifier(chain.first().publicKey as RSAPublicKey)))
        assertEquals(claims.toJSONObject(), grant.jwtClaimsSet.toJSONObject())
    }

    @Test
    fun `refuses a certificate chain whose first certificate is for another key`() {
        val chain = certificateChain(rsaKeyPair().public)

        val e = assertFailsWith<IllegalArgumentException> { GrantSigner(kms, chain.joinToString("") { pem(it) }) }

        assertContains(e.message!!, "not for the KMS key")
    }

    @Test
    fun `refuses an empty certificate chain`() {
        val e = assertFailsWith<IllegalArgumentException> { GrantSigner(kms, "") }

        assertContains(e.message!!, "no certificates")
    }
}
