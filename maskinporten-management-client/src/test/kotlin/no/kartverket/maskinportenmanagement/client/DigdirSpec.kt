package no.kartverket.maskinportenmanagement.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import no.kartverket.maskinportenmanagement.client.http.HttpMethod
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Checks an answer against Digdir's OpenAPI spec, more strictly than the spec itself: a field the spec does not
 * describe is a mismatch, since a test answer with such a field could hide that we read something Digdir never sends.
 * It knows only the parts of JSON Schema the spec uses, and throws on anything else rather than let it pass unchecked.
 */
class DigdirSpec private constructor(private val spec: JsonObject) {

    fun mismatches(method: HttpMethod, path: String, status: Int, body: String): List<String> {
        val content = spec.at("paths", path, method.name.lowercase(), "responses", status.toString(), "content")
        val schema = content.values.single().jsonObject.at("schema")
        return buildList { check(Json.parseToJsonElement(body), schema, "$") }
    }

    private fun MutableList<String>.check(value: JsonElement, schema: JsonObject, at: String) {
        val unknown = schema.keys - KEYWORDS
        require(unknown.isEmpty()) { "DigdirSpec does not know the keywords $unknown, used at $at" }
        schema[REF]?.let { return check(value, resolve(it.jsonPrimitive.content), at) }

        val type = schema["type"]?.jsonPrimitive?.content
        if (type != null && !value.isOf(type)) {
            val actual = when (value) {
                is JsonObject -> "an object"
                is JsonArray -> "an array"
                is JsonPrimitive -> value.toString()
            }
            add("$at: expected $type, got $actual")
            return
        }
        schema["enum"]?.let { if (value !in it as JsonArray) add("$at: $value is not one of $it") }
        schema["format"]?.let { format -> formatMismatch(format.jsonPrimitive.content, value)?.let { add("$at: $it") } }

        when (value) {
            is JsonObject -> checkMembers(value, schema, at)

            is JsonArray -> {
                schema["items"]?.let { items -> value.forEachIndexed { i, item -> check(item, items.jsonObject, "$at[$i]") } }
                if (schema["uniqueItems"] == JsonPrimitive(true) && value.toSet().size != value.size) add("$at: has duplicates")
            }

            is JsonPrimitive -> {}
        }
    }

    private fun MutableList<String>.checkMembers(value: JsonObject, schema: JsonObject, at: String) {
        val properties = schema["properties"]?.jsonObject.orEmpty()
        val additional = schema["additionalProperties"]
        for ((name, member) in value) {
            when (val memberSchema = properties[name] ?: additional) {
                is JsonObject -> check(member, memberSchema, "$at.$name")
                JsonPrimitive(true) -> {}
                else -> add("$at.$name: not in the spec")
            }
        }
    }

    private fun JsonElement.isOf(type: String): Boolean = when (type) {
        "object" -> this is JsonObject
        "array" -> this is JsonArray
        "string" -> this is JsonPrimitive && isString
        "boolean" -> this is JsonPrimitive && !isString && booleanOrNull != null
        "integer" -> this is JsonPrimitive && !isString && longOrNull != null
        "number" -> this is JsonPrimitive && !isString && doubleOrNull != null
        "null" -> this == JsonNull
        else -> throw IllegalArgumentException("DigdirSpec does not know the type $type")
    }

    private fun formatMismatch(format: String, value: JsonElement): String? {
        val text = (value as? JsonPrimitive)?.content ?: return null
        return when (format) {
            "date-time" -> try {
                OffsetDateTime.parse(text)
                null
            } catch (_: DateTimeParseException) {
                "$value is not a date-time"
            }

            "int32" -> if (text.toIntOrNull() == null) "$value is not an int32" else null

            "int64" -> if (text.toLongOrNull() == null) "$value is not an int64" else null

            else -> throw IllegalArgumentException("DigdirSpec does not know the format $format")
        }
    }

    private fun resolve(ref: String): JsonObject {
        require(ref.startsWith("#/")) { "DigdirSpec only follows references inside the spec, not $ref" }
        return spec.at(*ref.removePrefix("#/").split("/").toTypedArray())
    }

    private fun JsonObject.at(vararg keys: String): JsonObject = keys.fold(this) { node, key ->
        requireNotNull(node[key] as? JsonObject) { "Digdir's spec has no ${keys.joinToString(" > ")}" }
    }

    companion object {
        private const val REF = "\$ref"
        private val KEYWORDS = setOf(
            REF, "type", "properties", "additionalProperties", "items", "uniqueItems", "enum", "format", "description", "readOnly",
        )

        fun load(): DigdirSpec {
            val path = requireNotNull(System.getProperty("digdirSpec")) { "Set the system property digdirSpec to Digdir's spec" }
            return DigdirSpec(Json.parseToJsonElement(File(path).readText()).jsonObject)
        }
    }
}
