package no.kartverket.maskinportenmanagement.client.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaskinportenManagementExceptionTest {

    @Test
    fun `appends a short body to the message`() {
        assertEquals("boom: access denied", MaskinportenException("boom", responseBody = "access denied").message)
    }

    @Test
    fun `leaves the message alone when there is no body`() {
        assertEquals("boom", MaskinportenException("boom").message)
        assertEquals("boom", MaskinportenException("boom", responseBody = "").message)
    }

    @Test
    fun `abbreviates a long body in the message but keeps it whole on the exception`() {
        val body = "x".repeat(600)

        val e = MaskinportenException("boom", responseBody = body)

        assertTrue(e.message!!.startsWith("boom: " + "x".repeat(500)))
        assertTrue(e.message!!.endsWith("… (600 characters in total)"))
        assertEquals(body, e.responseBody, "the full body stays available for callers that want it")
    }
}
