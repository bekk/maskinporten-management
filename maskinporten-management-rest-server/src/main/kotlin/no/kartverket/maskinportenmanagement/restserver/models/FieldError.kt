package no.kartverket.maskinportenmanagement.restserver.models

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import no.kartverket.maskinportenmanagement.client.validation.ValidationCode

@Serializable
data class FieldError(
    @JsonSchema.Description("The query parameter, path parameter, header or body field, for example `scope`.")
    val field: String,

    @JsonSchema.Description(
        """- `MISSING`: the field is absent or blank.
- `INVALID_FORMAT`: the field is there, but not valid.
- `NOT_ALLOWED`: the field is not accepted here, or was sent more than once.""",
    )
    val code: ValidationCode,

    @JsonSchema.Description("What is wrong, for people to read. The wording may change.")
    val message: String,
)
