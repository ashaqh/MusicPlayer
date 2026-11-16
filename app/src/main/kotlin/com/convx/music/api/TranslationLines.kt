package com.convx.music.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal fun fitLineCount(lines: List<String>, expectedCount: Int): List<String> =
    List(expectedCount) { index -> lines.getOrElse(index) { "" } }

internal fun parseTranslationContent(content: String): List<String>? {
    parseJsonArray(content)?.let { return it }

    val cleaned = content.replace("```json", "").replace("```", "").trim()
    parseJsonArray(cleaned)?.let { return it }

    val extractedArray = extractJsonArray(cleaned) ?: return null
    parseJsonArray(extractedArray)?.let { return it }

    return parsePlainLines(cleaned)
}

internal fun extractJsonArray(content: String): String? {
    val start = content.indexOf('[')
    val end = content.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    return content.substring(start, end + 1)
}

internal fun parsePlainLines(content: String): List<String> = content.lines()
    .filter { it.trim().isNotEmpty() }
    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }

internal fun parseJsonArray(content: String): List<String>? = runCatching {
    val element = Json.parseToJsonElement(content)
    if (element !is JsonArray) return@runCatching null
    element.map { item ->
        when (item) {
            is JsonNull -> ""
            is JsonPrimitive -> if (item.isString) item.content else item.toString()
            else -> item.toString()
        }
    }
}.getOrNull()
