package no.kartverket.maskinportenmanagement.client.validation

public enum class ValidationCode {
    MISSING,
    INVALID_FORMAT,
}

public data class ValidationError(
    val field: String,
    val code: ValidationCode,
    val message: String,
)

public object ScopeAccessValidation {

    /** A Maskinporten scope is a prefix and a subscope separated by a colon, e.g. "kartverk:matrikkel.read". */
    public val SCOPE_FORMAT: Regex = Regex("^[^\\s:]+:\\S+$")

    public fun validate(scope: String?): List<ValidationError> = listOfNotNull(scopeError(scope))

    internal fun scopeError(value: String?): ValidationError? =
        fieldError(value, "scope") {
            if (it.matches(SCOPE_FORMAT)) {
                null
            } else {
                "must be a prefix and a subscope separated by a colon, e.g. \"kartverk:matrikkel.read\""
            }
        }

    private inline fun fieldError(
        value: String?,
        field: String,
        problem: (String) -> String?,
    ): ValidationError? {
        if (value.isNullOrBlank()) {
            return ValidationError(field, ValidationCode.MISSING, "$field is required")
        }
        return problem(value)?.let { ValidationError(field, ValidationCode.INVALID_FORMAT, "$field $it") }
    }
}
