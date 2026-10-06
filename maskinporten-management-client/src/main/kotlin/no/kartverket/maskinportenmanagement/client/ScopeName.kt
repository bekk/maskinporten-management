package no.kartverket.maskinportenmanagement.client

import no.kartverket.maskinportenmanagement.client.exception.ValidationException
import no.kartverket.maskinportenmanagement.client.validation.ScopeAccessValidation
import no.kartverket.maskinportenmanagement.client.validation.ValidationError

@JvmInline
public value class ScopeName private constructor(public val value: String) {
    public companion object {
        public fun parse(value: String): ScopeName = ScopeName(valid(value, ScopeAccessValidation::scopeError))
    }
}

private fun valid(value: String, error: (String?) -> ValidationError?): String {
    error(value)?.let { throw ValidationException(listOf(it)) }
    return value
}
