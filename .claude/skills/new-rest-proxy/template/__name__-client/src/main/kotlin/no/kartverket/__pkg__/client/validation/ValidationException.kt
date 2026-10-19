package no.kartverket.__pkg__.client.validation

public enum class ValidationCode {
    MISSING,
    INVALID_FORMAT,
}

public class ValidationError(
    public val field: String,
    public val code: ValidationCode,
    public val message: String,
)

/** The input was refused before __Upstream__ was called. [errors] lists every field that failed, not just the first. */
public class ValidationException(
    public val errors: List<ValidationError>,
) : RuntimeException(errors.joinToString("; ") { it.message }) {
    init {
        require(errors.isNotEmpty()) { "errors must not be empty" }
    }
}
