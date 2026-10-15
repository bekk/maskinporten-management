package no.kartverket.maskinportenmanagement.restserver

import java.net.URI

internal enum class DigdirEnvironment(val digdirBaseUrl: URI, val maskinportenWellKnownUrl: URI) {
    TEST(
        digdirBaseUrl = URI("https://api.test.samarbeid.digdir.no"),
        maskinportenWellKnownUrl = URI("https://test.maskinporten.no/.well-known/oauth-authorization-server"),
    ),
    PROD(
        digdirBaseUrl = URI("https://api.samarbeid.digdir.no"),
        maskinportenWellKnownUrl = URI("https://maskinporten.no/.well-known/oauth-authorization-server"),
    ),

    // The mock API in mock-api, which plays both Digdir and Maskinporten
    LOCAL(
        digdirBaseUrl = URI("http://localhost:8090"),
        maskinportenWellKnownUrl = URI("http://localhost:8090/.well-known/oauth-authorization-server"),
    ),
}
