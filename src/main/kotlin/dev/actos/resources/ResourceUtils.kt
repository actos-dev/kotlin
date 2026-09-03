package dev.actos.resources

import dev.actos.ActosJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal inline fun <reified T> T.toJsonRequestBody(): RequestBody =
    ActosJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

internal fun normalizeSparseContent(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> {
            val map = element.toMutableMap()
            val isContent =
                map.containsKey("id") &&
                    (map.containsKey("title") || map.containsKey("score") || map.containsKey("body"))
            if (isContent) {
                map.putIfAbsent(
                    "author",
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(""),
                            "username" to JsonPrimitive(""),
                            "actor_type" to JsonPrimitive("human"),
                            "created_at" to JsonPrimitive(""),
                            "trust_level" to JsonPrimitive(0),
                        ),
                    ),
                )
                map.putIfAbsent("author_deleted", JsonPrimitive(false))
                map.putIfAbsent("body", JsonPrimitive(""))
                map.putIfAbsent("body_format", JsonPrimitive("plain"))
                map.putIfAbsent("comment_count", JsonPrimitive(0))
                map.putIfAbsent("content_type", JsonPrimitive("post"))
                map.putIfAbsent("created_at", JsonPrimitive(""))
                map.putIfAbsent("deleted", JsonPrimitive(false))
                map.putIfAbsent("downvotes", JsonPrimitive(0))
                map.putIfAbsent("metadata", JsonNull)
                map.putIfAbsent("score", JsonPrimitive(0))
                map.putIfAbsent("tags", JsonArray(emptyList()))
                map.putIfAbsent("upvotes", JsonPrimitive(0))
            }
            // Recursively normalize any child arrays or objects
            for ((k, v) in element) {
                if (v is JsonArray || v is JsonObject) {
                    map[k] = normalizeSparseContent(v)
                }
            }
            JsonObject(map)
        }
        is JsonArray -> JsonArray(element.map { normalizeSparseContent(it) })
        else -> element
    }

internal inline fun <reified T> Response.parseJson(): T {
    val bodyStr = body?.string().orEmpty()
    if (bodyStr.isEmpty() && Unit is T) {
        return Unit as T
    }
    val element = ActosJson.parseToJsonElement(bodyStr)
    val normalized = normalizeSparseContent(element)
    return ActosJson.decodeFromJsonElement<T>(normalized)
}
