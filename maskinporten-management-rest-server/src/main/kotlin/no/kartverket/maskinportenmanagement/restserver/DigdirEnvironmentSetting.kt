package no.kartverket.maskinportenmanagement.restserver

import no.kartverket.maskinportenmanagement.client.DigdirEnvironment
import java.net.URI

// LOCAL is the mock API in mock-api, which plays both Digdir and Maskinporten. The client library has no environment
// for it, so the server gives the client the mock's URLs
internal enum class DigdirEnvironmentSetting(val digdirBaseUrl: URI, val maskinportenWellKnownUrl: URI) {
    TEST(DigdirEnvironment.TEST),
    PROD(DigdirEnvironment.PROD),
    LOCAL(
        digdirBaseUrl = URI("http://localhost:8090"),
        maskinportenWellKnownUrl = URI("http://localhost:8090/.well-known/oauth-authorization-server"),
    ),
    ;

    constructor(environment: DigdirEnvironment) : this(environment.digdirBaseUrl, environment.maskinportenWellKnownUrl)
}
