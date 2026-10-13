package no.kartverket.maskinportenmanagement.client

@JvmInline
public value class OrganizationNumber private constructor(public val value: String) {
    override fun toString(): String = value

    public companion object {
        private val NINE_DIGITS = Regex("[0-9]{9}")

        // Digdir gets it in the path, where ".." would turn DELETE .../access/.. into DELETE /api/v1/scopes: deleting
        // the scope itself
        public fun of(value: String): OrganizationNumber {
            require(NINE_DIGITS.matches(value)) { "Expected an organisation number of 9 digits, but was \"$value\"" }
            return OrganizationNumber(value)
        }
    }
}
