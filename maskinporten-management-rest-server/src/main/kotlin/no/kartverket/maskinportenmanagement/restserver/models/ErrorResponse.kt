package no.kartverket.maskinportenmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    @JsonSchema.Description("A short summary for people to read. The wording may change, so check `code` in your code.")
    val error: String,

    @JsonSchema.Description(
        """What went wrong. These values do not change, so your code can rely on them.

- `INVALID_REQUEST` (400): the request was refused before it reached Digdir. `error` says why.
- `UPSTREAM_ERROR` (502): this API could not get a usable answer from Digdir or Maskinporten, from the
  service that says which scopes the calling app has access to, or from Cloud KMS, which signs this
  API's requests for Maskinporten tokens. Your request did not cause it.
- `INTERNAL_ERROR` (500): an unexpected error in this API.""",
    )
    val code: ErrorCode,
)
