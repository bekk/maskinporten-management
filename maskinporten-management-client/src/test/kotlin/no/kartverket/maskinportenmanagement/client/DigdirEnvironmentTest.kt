package no.kartverket.maskinportenmanagement.client

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.URI
import kotlin.test.assertEquals

class DigdirEnvironmentTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(DigdirEnvironment::class)
    fun `each environment is Digdir and Maskinporten in that environment`(environment: DigdirEnvironment) {
        val urls = when (environment) {
            DigdirEnvironment.TEST -> listOf(
                "https://api.test.samarbeid.digdir.no",
                "https://test.maskinporten.no/.well-known/oauth-authorization-server",
            )

            DigdirEnvironment.PROD -> listOf(
                "https://api.samarbeid.digdir.no",
                "https://maskinporten.no/.well-known/oauth-authorization-server",
            )
        }

        assertEquals(urls.map(::URI), listOf(environment.digdirBaseUrl, environment.maskinportenWellKnownUrl))
    }
}
