package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = ScopeSerializer::class)
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

internal object ScopeSerializer : KSerializer<Scope> {
    override val descriptor = PrimitiveSerialDescriptor("Scope", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Scope): Unit = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): Scope = Scope.of(decoder.decodeString())
}
