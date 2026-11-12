package no.kartverket.maskinportenmanagement.client

public sealed class MaskinportenManagementException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
