package no.kartverket.maskinportenmanagement.restserver.models

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    @JsonSchema.Description(
        """What went wrong, as a name that does not change, so your code can rely on it.

- `tag:kartverket.no,2026:validation-error` (400): one or more query parameters, path parameters, headers or body
  fields are missing, invalid or not allowed. `errors` lists all of them.
- `tag:kartverket.no,2026:malformed-request` (400): the request could not be read, for example a query that is not a
  list of names and values, or a body that is not JSON in UTF-8 sent with `Content-Type: application/json`.
- `tag:kartverket.no,2026:not-found` (404): there is no endpoint at this path.
- `tag:kartverket.no,2026:method-not-allowed` (405): the endpoint at this path does not accept this HTTP method.
- `tag:kartverket.no,2026:upstream-error` (502): this API could not get an answer from Digdir, from the service that
  says which scopes the calling app has access to, or from Cloud KMS, which signs this API's requests for
  Maskinporten tokens. Your request did not cause it.
- `tag:kartverket.no,2026:internal-error` (500): an unexpected error in this API.""",
    )
    val type: String,

    @JsonSchema.Description("A short name for the `type`, the same every time.")
    val title: String,

    @JsonSchema.Description("The HTTP status code, the same as the response's.")
    val status: Int,

    @JsonSchema.Description(
        "What went wrong this time, for people to read. Left out when `title` says it all. The wording may change, " +
            "so check `type` in your code.",
    )
    val detail: String? = null,

    @JsonSchema.Type(JsonType.ARRAY)
    @JsonSchema.Description(
        "Only with `tag:kartverket.no,2026:validation-error`: every field that failed, so you can fix them all at once.",
    )
    val errors: List<FieldError>? = null,
) {
    companion object {
        fun of(type: ProblemType, status: HttpStatusCode, detail: String? = null, errors: List<FieldError>? = null) =
            ErrorResponse(type.uri, type.title, status.value, detail, errors)
    }
}
