package no.kartverket.maskinportenmanagement.client.filtering

@JvmInline
public value class ClientCertificate private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): ClientCertificate = ClientCertificate(value)
    }
}
