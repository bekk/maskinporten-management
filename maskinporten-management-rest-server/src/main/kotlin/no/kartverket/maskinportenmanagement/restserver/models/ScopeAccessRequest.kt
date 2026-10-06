package no.kartverket.maskinportenmanagement.restserver.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.kartverket.maskinportenmanagement.client.ScopeName
import no.kartverket.maskinportenmanagement.client.exception.ValidationException
import no.kartverket.maskinportenmanagement.client.validation.ScopeAccessValidation

@Serializable(with = ScopeAccessRequest.Serializer::class)
data class ScopeAccessRequest(
    val scope: ScopeName,
) {
    object Serializer : KSerializer<ScopeAccessRequest> {
        override val descriptor: SerialDescriptor = Raw.serializer().descriptor

        override fun deserialize(decoder: Decoder): ScopeAccessRequest {
            val raw = decoder.decodeSerializableValue(Raw.serializer())
            val errors = ScopeAccessValidation.validate(raw.scope)
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return ScopeAccessRequest(ScopeName.parse(raw.scope!!))
        }

        override fun serialize(encoder: Encoder, value: ScopeAccessRequest) {
            encoder.encodeSerializableValue(Raw.serializer(), Raw(value.scope.value))
        }
    }

    @Serializable
    @SerialName("ScopeAccessRequest")
    private data class Raw(
        val scope: String? = null,
    )
}
