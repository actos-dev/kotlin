package dev.actos.resources

import dev.actos.ActosJson
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal inline fun <reified T> T.toJsonRequestBody(): RequestBody =
    ActosJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

internal inline fun <reified T> Response.parseJson(): T {
    val bodyStr = body?.string().orEmpty()
    return ActosJson.decodeFromString<T>(bodyStr)
}
