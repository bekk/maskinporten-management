package no.kartverket.maskinportenmanagement.client.support

import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator

internal object TestKeys {
    val rsa: RSAKey by lazy { RSAKeyGenerator(2048).keyID("test-key").generate() }
}
