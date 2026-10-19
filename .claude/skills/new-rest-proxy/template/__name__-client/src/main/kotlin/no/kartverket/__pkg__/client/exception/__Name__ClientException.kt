package no.kartverket.__pkg__.client.exception

/**
 * Every failure to get an answer from __Upstream__. [statusCode] and [responseBody] are __Upstream__'s answer, when it
 * gave one. The message shows the start of the body, so a log line says what went wrong without flooding the log.
 */
public sealed class __Name__ClientException(
    message: String,
    public val statusCode: Int? = null,
    public val responseBody: String? = null,
    cause: Throwable? = null,
) : RuntimeException(messageWithBody(message, responseBody), cause) {

    private companion object {
        private const val MAX_BODY_LENGTH = 500

        private fun messageWithBody(message: String, body: String?): String {
            val shown = abbreviate(body)
            return if (shown.isEmpty()) message else "$message: $shown"
        }

        private fun abbreviate(body: String?): String {
            if (body == null) return ""
            return if (body.length <= MAX_BODY_LENGTH) {
                body
            } else {
                "${body.take(MAX_BODY_LENGTH)}… (${body.length} characters in total)"
            }
        }
    }
}
