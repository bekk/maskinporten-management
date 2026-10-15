package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.slf4j.LoggerFactory
import kotlin.enums.EnumEntries

// A value Digdir adds becomes UNKNOWN, so one new value does not make the whole answer unreadable
internal abstract class OrUnknownSerializer<T : Enum<T>>(
    private val known: KSerializer<T>,
    private val entries: EnumEntries<T>,
    private val unknown: T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = known.descriptor

    override fun serialize(encoder: Encoder, value: T): Unit = known.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): T {
        val name = decoder.decodeString()
        val index = descriptor.getElementIndex(name)
        if (index != CompositeDecoder.UNKNOWN_NAME) return entries[index]
        log.warn("Unknown ${descriptor.serialName.substringAfterLast('.')} from Digdir: \"$name\"")
        return unknown
    }

    private companion object {
        val log = LoggerFactory.getLogger(OrUnknownSerializer::class.java)
    }
}
