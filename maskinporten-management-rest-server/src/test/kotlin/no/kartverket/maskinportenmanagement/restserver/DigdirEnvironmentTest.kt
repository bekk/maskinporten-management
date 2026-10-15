package no.kartverket.maskinportenmanagement.restserver

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class DigdirEnvironmentTest {

    @Test
    fun `each environment calls Digdir and Maskinporten in that environment`() {
        val expected = mapOf(
            DigdirEnvironment.TEST to listOf(
                "https://api.test.samarbeid.digdir.no",
                "https://test.maskinporten.no/.well-known/oauth-authorization-server",
            ),
            DigdirEnvironment.PROD to listOf(
                "https://api.samarbeid.digdir.no",
                "https://maskinporten.no/.well-known/oauth-authorization-server",
            ),
            DigdirEnvironment.LOCAL to listOf(
                "http://localhost:8090",
                "http://localhost:8090/.well-known/oauth-authorization-server",
            ),
        )

        for ((environment, urls) in expected) {
            assertEquals(urls.map(::URI), listOf(environment.digdirBaseUrl, environment.maskinportenWellKnownUrl), environment.name)
        }
    }
}
