package no.kartverket.__pkg__.client.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class __Name__ClientExceptionTest {

    @Test
    fun `the message shows a short body as it is`() {
        val e = __Upstream__Exception("__Upstream__ answered HTTP 500", 500, "oops")

        assertEquals("__Upstream__ answered HTTP 500: oops", e.message)
    }

    @Test
    fun `the message cuts a long body, but the exception keeps all of it`() {
        val body = "x".repeat(2000)

        val e = __Upstream__Exception("__Upstream__ answered HTTP 500", 500, body)

        assertTrue(e.message!!.endsWith("… (2000 characters in total)"), e.message)
        assertEquals(body, e.responseBody)
    }

    @Test
    fun `the message is left alone without a body`() {
        assertEquals("Call to __Upstream__ failed", __Upstream__Exception("Call to __Upstream__ failed").message)
    }
}
