package com.maskinporten

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

suspend fun HttpResponse.json(): JsonElement = Json.parseToJsonElement(bodyAsText())

suspend fun HttpResponse.jsonObject(): JsonObject = json().jsonObject

suspend fun HttpResponse.jsonObjects(): List<JsonObject> = json().jsonArray.map { it.jsonObject }

fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

fun JsonObject.strings(key: String): List<String> = this[key]!!.jsonArray.map { it.jsonPrimitive.content }
