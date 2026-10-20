package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.DigdirEnvironment
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class DigdirEnvironmentSettingTest {

    @Test
    fun `TEST and PROD call Digdir and Maskinporten in the client's environment of the same name`() {
        for (environment in DigdirEnvironment.entries) {
            val setting = DigdirEnvironmentSetting.valueOf(environment.name)

            assertEquals(environment.digdirBaseUrl, setting.digdirBaseUrl, environment.name)
            assertEquals(environment.maskinportenWellKnownUrl, setting.maskinportenWellKnownUrl, environment.name)
        }
    }

    @Test
    fun `LOCAL calls the mock API for both Digdir and Maskinporten`() {
        assertEquals(URI("http://localhost:8090"), DigdirEnvironmentSetting.LOCAL.digdirBaseUrl)
        assertEquals(
            URI("http://localhost:8090/.well-known/oauth-authorization-server"),
            DigdirEnvironmentSetting.LOCAL.maskinportenWellKnownUrl,
        )
    }
}
