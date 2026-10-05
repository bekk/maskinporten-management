package no.kartverket.maskinportenmanagement.client.http

import java.net.http.HttpClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class JavaManagementHttpClientTest {

    @Test
    fun `refuses an HttpClient that follows redirects`() {
        val following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

        val e = assertFailsWith<IllegalArgumentException> { JavaManagementHttpClient(following, Duration.ofSeconds(3)) }

        assertContains(e.message!!, "redirects")
    }

    @Test
    fun `rejects a request timeout that is not positive`() {
        val e = assertFailsWith<IllegalArgumentException> { JavaManagementHttpClient(HttpClient.newHttpClient(), Duration.ZERO) }

        assertContains(e.message!!, "positive")
    }
}
