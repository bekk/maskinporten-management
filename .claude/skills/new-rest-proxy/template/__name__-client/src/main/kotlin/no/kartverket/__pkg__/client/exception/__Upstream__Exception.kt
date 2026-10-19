package no.kartverket.__pkg__.client.exception

/**
 * The call to __Upstream__'s API failed. Without a [statusCode], __Upstream__ did not answer. With one, it answered
 * with an error, or with something this client cannot read.
 */
public class __Upstream__Exception internal constructor(
    message: String,
    statusCode: Int? = null,
    responseBody: String? = null,
    cause: Throwable? = null,
) : __Name__ClientException(message, statusCode, responseBody, cause)
