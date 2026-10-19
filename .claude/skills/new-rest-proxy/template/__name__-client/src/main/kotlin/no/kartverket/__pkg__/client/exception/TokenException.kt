package no.kartverket.__pkg__.client.exception

/** Getting the access token for __Upstream__ failed, so __Upstream__ was never called. */
public class TokenException internal constructor(
    message: String,
    statusCode: Int? = null,
    responseBody: String? = null,
    cause: Throwable? = null,
) : __Name__ClientException(message, statusCode, responseBody, cause)
