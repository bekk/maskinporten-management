package no.kartverket.maskinportenmanagement.client.exception

public class MaskinportenException internal constructor(
    message: String,
    statusCode: Int? = null,
    responseBody: String? = null,
    cause: Throwable? = null,
) : MaskinportenManagementException(message, statusCode, responseBody, cause)
