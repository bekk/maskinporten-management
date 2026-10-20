package no.kartverket.maskinportenmanagement.client

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class DigdirEnvironmentTest {

    @Test
    fun `each environment is Digdir and Maskinporten in that environment`() {
        val expected = mapOf(
            DigdirEnvironment.TEST to listOf(
                "https://api.test.samarbeid.digdir.no",
                "https://test.maskinporten.no/.well-known/oauth-authorization-server",
            ),
            DigdirEnvironment.PROD to listOf(
                "https://api.samarbeid.digdir.no",
                "https://maskinporten.no/.well-known/oauth-authorization-server",
            ),
        )

        assertEquals(DigdirEnvironment.entries.toSet(), expected.keys)
        for ((environment, urls) in expected) {
            assertEquals(urls.map(::URI), listOf(environment.digdirBaseUrl, environment.maskinportenWellKnownUrl), environment.name)
        }
    }
}
