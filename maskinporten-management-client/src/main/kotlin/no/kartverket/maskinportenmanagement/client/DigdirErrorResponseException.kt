package no.kartverket.maskinportenmanagement.client

public class DigdirErrorResponseException(
    public val statusCode: Int,
    public val contentType: String?,
    public val body: ByteArray,
) : RuntimeException("Digdir answered HTTP $statusCode")
