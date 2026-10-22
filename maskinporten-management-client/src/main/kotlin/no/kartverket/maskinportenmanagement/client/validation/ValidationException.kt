package no.kartverket.maskinportenmanagement.client.validation

public enum class ValidationCode {
    MISSING,
    INVALID_FORMAT,
    NOT_ALLOWED,
}

public class ValidationError(
    public val field: String,
    public val code: ValidationCode,
    public val message: String,
)

public class ValidationException(
    public val errors: List<ValidationError>,
) : IllegalArgumentException(errors.joinToString("; ") { it.message }) {
    init {
        require(errors.isNotEmpty()) { "errors must not be empty" }
    }
}
