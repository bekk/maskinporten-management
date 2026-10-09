package no.kartverket.maskinportenmanagement.client.http

import java.net.URI

public fun interface DigdirHttpClient {
    public suspend fun send(request: DigdirHttpRequest): DigdirHttpResponse
}

public class DigdirHttpRequest internal constructor(
    public val method: String,
    public val url: URI,
    public val headers: Map<String, String>,
    public val body: ByteArray? = null,
)

public class DigdirHttpResponse(
    public val statusCode: Int,
    public val contentType: String?,
    public val body: ByteArray,
)
