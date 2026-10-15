package no.kartverket.maskinportenmanagement.client

@JvmInline
public value class ScopeUpdateJson private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun of(value: String): ScopeUpdateJson = ScopeUpdateJson(value)
    }
}
