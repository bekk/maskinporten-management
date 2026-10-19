package no.kartverket.__pkg__.client

import java.net.URI

public enum class __Upstream__Environment(public val baseUrl: URI) {
    // TODO: __Upstream__'s base URLs for test and production
    TEST(baseUrl = URI("https://api.test.example.invalid")),
    PROD(baseUrl = URI("https://api.example.invalid")),
}
