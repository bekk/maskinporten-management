package com.maskinporten.models.integrations

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@Serializable
data class Integration(
    @Serializable(with = UtcLocalDateTimeSerializer::class)
    val created: Instant,
    val id: String,
    val description: String? = null,
    val customer_organization_number: String,
    val supplier_organization_number: String? = null,
    val supplier_integration_id: String? = null,
    val integration_type: IntegrationType,
    @Serializable(with = UtcLocalDateTimeSerializer::class)
    val last_updated: Instant,
    val active: Boolean
)

object UtcLocalDateTimeSerializer : KSerializer<Instant> {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC)

    override val descriptor = PrimitiveSerialDescriptor("UtcLocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(formatter.format(value.toJavaInstant()))

    override fun deserialize(decoder: Decoder): Instant =
        LocalDateTime.parse(decoder.decodeString()).toInstant(ZoneOffset.UTC).toKotlinInstant()
}

enum class IntegrationType {
    IDPORTEN_SAML2,
    IDPORTEN_SAML2_ONBEHALF,
    IDPORTEN_OIDC,
    IDPORTEN_OIDC_ONBEHALFOF,
    KRR_REST,
    KRR_REST_ONBEHALFOF,
    KRR_SOAP,
    KRR_SOAP_ONBEHALFOF,
    EFORMIDLING_DPI,
    EFORMIDLING_DPI_ONBEHALFOF,
    EFORMIDLING_DPV,
    EFORMIDLING_DPV_ONBEHALFOF,
    EFORMIDLING_DPO,
    EFORMIDLING_DPO_ONBEHALFOF,
    EFORMIDLING_DPE,
    EFORMIDLING_DPE_ONBEHALFOF,
    EFORMIDLING_DPF,
    EFORMIDLING_DPF_ONBEHALFOF,
    API_KLIENT,
    MASKINPORTEN
}
