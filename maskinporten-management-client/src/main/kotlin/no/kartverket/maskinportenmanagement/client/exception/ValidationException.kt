package no.kartverket.maskinportenmanagement.client.exception

import no.kartverket.maskinportenmanagement.client.validation.ValidationError

public class ValidationException(
    public val errors: List<ValidationError>,
) : RuntimeException(errors.joinToString("; ") { it.message })
