package no.kartverket.maskinportenmanagement.client

@JvmInline
public value class Scope private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        // Maskinporten takes the scopes space-separated in one claim, so whitespace would turn one scope into several
        public fun of(value: String): Scope {
            require(value.isNotEmpty() && value.none(Char::isWhitespace)) {
                "Expected a scope that is non-empty and without whitespace, but was \"$value\""
            }
            return Scope(value)
        }
    }
}
