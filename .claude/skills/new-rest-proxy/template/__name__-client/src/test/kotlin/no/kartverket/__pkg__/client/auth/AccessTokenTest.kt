package no.kartverket.__pkg__.client.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccessTokenTest {

    @Test
    fun `keeps the token`() {
        assertEquals("secret-token", AccessToken.of("secret-token").value)
    }

    @Test
    fun `does not show the token when printed`() {
        val token = AccessToken.of("secret-token")

        assertFalse("secret-token" in "Calling with $token")
    }
}
