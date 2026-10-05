package no.kartverket.maskinportenmanagement.client.http

import java.net.URI

public fun interface ManagementHttpClient {
    public suspend fun send(request: ManagementHttpRequest): ManagementHttpResponse
}

public class ManagementHttpRequest internal constructor(
    public val method: String,
    public val url: URI,
    public val headers: Map<String, String>,
    public val body: String? = null,
)

public class ManagementHttpResponse(
    public val statusCode: Int,
    public val body: String,
)
