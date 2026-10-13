package no.kartverket.maskinportenmanagement.client

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrganizationNumberTest {

    @Test
    fun `accepts 9 digits`() {
        assertEquals("311718371", OrganizationNumber.of("311718371").toString())
    }

    @Test
    fun `refuses anything but 9 digits`() {
        for (raw in listOf("..", ".", "", "31171837", "3117183710", "31171837a", "311718371/..", " 311718371")) {
            val e = assertFailsWith<IllegalArgumentException>(raw) { OrganizationNumber.of(raw) }

            assertContains(e.message!!, "9 digits", message = raw)
        }
    }
}
