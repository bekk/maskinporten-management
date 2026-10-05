package no.kartverket.maskinportenmanagement.client.auth

internal object DigdirScopes {
    /** Reading a single scope and its consumers needs read; creating and changing needs write. */
    const val SCOPES = "idporten:scopes.read idporten:scopes.write"
}
