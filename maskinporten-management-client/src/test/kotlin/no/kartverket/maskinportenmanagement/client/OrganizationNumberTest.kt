package no.kartverket.maskinportenmanagement.client

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrganizationNumberTest {

    @Test
    fun `accepts 9 digits`() {
        assertEquals("311718371", OrganizationNumber.of("311718371").toString())
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = ["..", ".", "", "31171837", "3117183710", "31171837a", "311718371/..", " 311718371"])
    fun `refuses anything but 9 digits`(raw: String) {
        val e = assertFailsWith<IllegalArgumentException> { OrganizationNumber.of(raw) }

        assertContains(e.message!!, "9 digits")
    }
}
