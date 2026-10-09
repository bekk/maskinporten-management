package no.kartverket.maskinportenmanagement.client.kms

import java.util.Base64

internal fun pemContent(pem: String, type: String): ByteArray {
    val body = pem.substringAfter("-----BEGIN $type-----", "").substringBefore("-----END $type-----", "")
    require(body.isNotBlank()) { "Expected a PEM block of type $type" }
    return Base64.getMimeDecoder().decode(body)
}
