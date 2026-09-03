package dev.actos

import dev.actos.model.ErrorCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

private const val STATUS_BAD_REQUEST: Int = 400
private const val STATUS_UNAUTHORIZED: Int = 401
private const val STATUS_FORBIDDEN: Int = 403
private const val STATUS_NOT_FOUND: Int = 404
private const val STATUS_CONFLICT: Int = 409
private const val STATUS_GONE: Int = 410
private const val STATUS_UNSUPPORTED_MEDIA_TYPE: Int = 415
private const val STATUS_TOO_MANY_REQUESTS: Int = 429
private const val STATUS_INTERNAL_SERVER_ERROR_MIN: Int = 500
private const val STATUS_INTERNAL_SERVER_ERROR_MAX: Int = 599
private const val MAX_FALLBACK_BODY_LENGTH: Int = 200

public sealed class ActosException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    public val isNotFound: Boolean get() = this is NotFoundException
    public val isGone: Boolean get() = this is GoneException
    public val isRateLimited: Boolean get() = this is RateLimitException
    public val isForbidden: Boolean get() = this is ForbiddenException
    public val isRetryable: Boolean
        get() = this is InternalServerException || this is RateLimitException || this is ActosTransportException
}

public open class ActosApiException(
    public val status: Int,
    public val code: ErrorCode?,
    public val detail: String,
    public val requestId: String?,
    public val rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosException(
        message = formatApiErrorMessage(status, code, detail, requestId),
        cause = cause,
    )

public open class ValidationException(
    status: Int = STATUS_BAD_REQUEST,
    detail: String = "Validation failed",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, ErrorCode.VALIDATION_FAILED, detail, requestId, rateLimit, cause)

public class InvalidCursorException(
    status: Int = STATUS_BAD_REQUEST,
    detail: String = "Invalid cursor",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, ErrorCode.INVALID_CURSOR, detail, requestId, rateLimit, cause)

public open class AuthenticationException(
    status: Int = STATUS_UNAUTHORIZED,
    code: ErrorCode? = ErrorCode.MISSING_CREDENTIALS,
    detail: String = "Missing or invalid credentials",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, code, detail, requestId, rateLimit, cause)

public class InvalidKeyException(
    status: Int = STATUS_UNAUTHORIZED,
    detail: String = "Invalid API key",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : AuthenticationException(status, ErrorCode.INVALID_KEY, detail, requestId, rateLimit, cause)

public open class ForbiddenException(
    status: Int = STATUS_FORBIDDEN,
    code: ErrorCode? = ErrorCode.FORBIDDEN,
    detail: String = "Access forbidden",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, code, detail, requestId, rateLimit, cause)

public class BannedException(
    status: Int = STATUS_FORBIDDEN,
    detail: String = "Account is banned",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ForbiddenException(status, ErrorCode.BANNED, detail, requestId, rateLimit, cause)

public class NotFoundException(
    status: Int = STATUS_NOT_FOUND,
    detail: String = "Resource not found",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, ErrorCode.NOT_FOUND, detail, requestId, rateLimit, cause)

public class ConflictException(
    status: Int = STATUS_CONFLICT,
    detail: String = "Resource conflict",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, ErrorCode.CONFLICT, detail, requestId, rateLimit, cause)

public class GoneException(
    status: Int = STATUS_GONE,
    detail: String = "Resource is gone",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, ErrorCode.GONE, detail, requestId, rateLimit, cause)

public class UnsupportedMediaException(
    status: Int = STATUS_UNSUPPORTED_MEDIA_TYPE,
    detail: String = "Unsupported media type",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, ErrorCode.UNSUPPORTED_MEDIA, detail, requestId, rateLimit, cause)

public class RateLimitException(
    status: Int = STATUS_TOO_MANY_REQUESTS,
    detail: String = "Rate limit exceeded",
    requestId: String? = null,
    public val retryAfter: Long? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, ErrorCode.RATE_LIMITED, detail, requestId, rateLimit, cause)

public class InternalServerException(
    status: Int = STATUS_INTERNAL_SERVER_ERROR_MIN,
    detail: String = "Internal server error",
    requestId: String? = null,
    rateLimit: RateLimit? = null,
    cause: Throwable? = null,
) : ActosApiException(status, ErrorCode.INTERNAL, detail, requestId, rateLimit, cause)

public sealed class ActosTransportException(
    message: String,
    cause: Throwable? = null,
) : ActosException(message, cause)

public class ApiTimeoutException(
    message: String,
    cause: Throwable? = null,
) : ActosTransportException(message, cause)

public class ApiConnectionException(
    message: String,
    cause: Throwable? = null,
) : ActosTransportException(message, cause)

private fun formatApiErrorMessage(
    status: Int,
    code: ErrorCode?,
    detail: String,
    requestId: String?,
): String {
    val codeTag = code?.name ?: "HTTP_$status"
    val reqTag = requestId ?: "none"
    return "[$status $codeTag] $detail (requestId=$reqTag)"
}

public fun createApiException(
    response: Response,
    bodyString: String?,
): ActosApiException {
    val status = response.code
    val rateLimit = RateLimit.fromHeaders(response.headers)
    val retryAfter = rateLimit?.retryAfter ?: response.header("Retry-After")?.toLongOrNull()
    val headerRequestId = response.header("x-request-id")

    val parsed = parseProblemPayload(bodyString)
    val finalRequestId = parsed.requestId ?: headerRequestId
    val finalDetail = parsed.detail.ifEmpty { "HTTP $status error" }
    val finalStatus = if (parsed.status > 0) parsed.status else status

    val ctx =
        ErrorContext(
            status = finalStatus,
            detail = finalDetail,
            requestId = finalRequestId,
            rateLimit = rateLimit,
            retryAfter = retryAfter,
        )

    return if (parsed.errorCode != null) {
        createExceptionFromErrorCode(parsed.errorCode, ctx)
    } else if (parsed.hasUnknownCode) {
        ActosApiException(
            status = ctx.status,
            code = null,
            detail = ctx.detail,
            requestId = ctx.requestId,
            rateLimit = ctx.rateLimit,
        )
    } else {
        createExceptionFromStatusCode(ctx)
    }
}

private data class ErrorContext(
    val status: Int,
    val detail: String,
    val requestId: String?,
    val rateLimit: RateLimit?,
    val retryAfter: Long?,
)

private data class ParsedPayload(
    val status: Int = 0,
    val errorCode: ErrorCode? = null,
    val hasUnknownCode: Boolean = false,
    val detail: String = "",
    val requestId: String? = null,
)

private fun parseProblemPayload(bodyString: String?): ParsedPayload {
    if (bodyString.isNullOrBlank()) {
        return ParsedPayload()
    }
    return try {
        val root = Json.parseToJsonElement(bodyString).jsonObject
        val statusVal = root["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val detailVal = root["detail"]?.jsonPrimitive?.content ?: root["title"]?.jsonPrimitive?.content ?: ""
        val reqIdVal = root["request_id"]?.jsonPrimitive?.content ?: root["requestId"]?.jsonPrimitive?.content
        val rawCode = root["code"]?.jsonPrimitive?.content

        val (matchedCode, isUnknown) =
            if (rawCode != null) {
                val found = ErrorCode.decode(rawCode)
                if (found != null) {
                    Pair(found, false)
                } else {
                    Pair(null, true)
                }
            } else {
                Pair(null, false)
            }

        ParsedPayload(
            status = statusVal,
            errorCode = matchedCode,
            hasUnknownCode = isUnknown,
            detail = detailVal,
            requestId = reqIdVal,
        )
    } catch (_: Exception) {
        val fallbackDetail = if (bodyString.length <= MAX_FALLBACK_BODY_LENGTH) bodyString.trim() else ""
        ParsedPayload(detail = fallbackDetail)
    }
}

private fun createExceptionFromErrorCode(
    code: ErrorCode,
    ctx: ErrorContext,
): ActosApiException =
    when (code) {
        ErrorCode.VALIDATION_FAILED ->
            ValidationException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ErrorCode.INVALID_CURSOR ->
            InvalidCursorException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ErrorCode.MISSING_CREDENTIALS ->
            AuthenticationException(
                ctx.status,
                ErrorCode.MISSING_CREDENTIALS,
                ctx.detail,
                ctx.requestId,
                ctx.rateLimit,
            )
        ErrorCode.INVALID_KEY ->
            InvalidKeyException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ErrorCode.FORBIDDEN ->
            ForbiddenException(
                ctx.status,
                ErrorCode.FORBIDDEN,
                ctx.detail,
                ctx.requestId,
                ctx.rateLimit,
            )
        ErrorCode.BANNED ->
            BannedException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ErrorCode.NOT_FOUND ->
            NotFoundException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ErrorCode.CONFLICT ->
            ConflictException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ErrorCode.GONE ->
            GoneException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ErrorCode.UNSUPPORTED_MEDIA ->
            UnsupportedMediaException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ErrorCode.RATE_LIMITED ->
            RateLimitException(
                ctx.status,
                ctx.detail,
                ctx.requestId,
                ctx.retryAfter,
                ctx.rateLimit,
            )
        ErrorCode.INTERNAL ->
            InternalServerException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
    }

private fun createExceptionFromStatusCode(ctx: ErrorContext): ActosApiException =
    when {
        ctx.status == STATUS_BAD_REQUEST ->
            ValidationException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ctx.status == STATUS_UNAUTHORIZED ->
            AuthenticationException(ctx.status, null, ctx.detail, ctx.requestId, ctx.rateLimit)
        ctx.status == STATUS_FORBIDDEN ->
            ForbiddenException(ctx.status, null, ctx.detail, ctx.requestId, ctx.rateLimit)
        ctx.status == STATUS_NOT_FOUND ->
            NotFoundException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ctx.status == STATUS_CONFLICT ->
            ConflictException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ctx.status == STATUS_GONE ->
            GoneException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ctx.status == STATUS_UNSUPPORTED_MEDIA_TYPE ->
            UnsupportedMediaException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        ctx.status == STATUS_TOO_MANY_REQUESTS ->
            RateLimitException(ctx.status, ctx.detail, ctx.requestId, ctx.retryAfter, ctx.rateLimit)
        ctx.status in STATUS_INTERNAL_SERVER_ERROR_MIN..STATUS_INTERNAL_SERVER_ERROR_MAX ->
            InternalServerException(ctx.status, ctx.detail, ctx.requestId, ctx.rateLimit)
        else ->
            ActosApiException(ctx.status, null, ctx.detail, ctx.requestId, ctx.rateLimit)
    }
