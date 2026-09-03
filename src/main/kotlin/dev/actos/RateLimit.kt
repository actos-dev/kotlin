package dev.actos

import okhttp3.Headers

public data class RateLimit(
    public val limit: Int,
    public val remaining: Int,
    public val reset: Long,
    public val retryAfter: Long? = null,
) {
    public companion object {
        public fun fromHeaders(headers: Headers): RateLimit? {
            val limit = headers["x-ratelimit-limit"]?.toIntOrNull()
            val remaining = headers["x-ratelimit-remaining"]?.toIntOrNull()
            val reset = headers["x-ratelimit-reset"]?.toLongOrNull()
            val retryAfter = headers["retry-after"]?.toLongOrNull()

            val anyPresent = listOfNotNull(limit, remaining, reset, retryAfter).isNotEmpty()
            if (!anyPresent) {
                return null
            }

            return RateLimit(
                limit = limit ?: -1,
                remaining = remaining ?: 0,
                reset = reset ?: 0L,
                retryAfter = retryAfter,
            )
        }
    }
}
