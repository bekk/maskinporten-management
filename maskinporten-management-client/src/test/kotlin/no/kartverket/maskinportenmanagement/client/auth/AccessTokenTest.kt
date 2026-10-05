package no.kartverket.maskinportenmanagement.client.auth

import no.kartverket.maskinportenmanagement.client.support.NOW
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessTokenTest {

    @Test
    fun `masks the token value in toString so it cannot reach the logs`() {
        for (token in listOf(MaskinportenToken("super-secret-jwt", NOW))) {
            val rendered = token.toString()
            assertFalse(rendered.contains("super-secret-jwt"), "the token value must never be printed")
            assertTrue(rendered.contains("***"))
            assertTrue(rendered.contains(NOW.toString()), "the expiry is safe to print and useful in logs")
        }
    }
}
