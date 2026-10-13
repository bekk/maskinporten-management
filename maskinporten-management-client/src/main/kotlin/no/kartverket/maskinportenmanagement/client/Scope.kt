package no.kartverket.maskinportenmanagement.client

@JvmInline
public value class Scope private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): Scope = Scope(value)
    }
}
