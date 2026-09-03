package dev.actos

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.random.Random

private const val HTTP_TOO_MANY_REQUESTS: Int = 429
private const val HTTP_CLIENT_ERROR_MIN: Int = 400
private const val HTTP_CLIENT_ERROR_MAX: Int = 499
private const val HTTP_SERVER_ERROR_MIN: Int = 500
private const val HTTP_SERVER_ERROR_MAX: Int = 599
private const val BASE_BACKOFF_MS: Long = 250L
private const val MAX_BACKOFF_MS: Long = 30000L
private const val MILLIS_PER_SECOND: Long = 1000L
private const val MASK_KEY_THRESHOLD: Int = 8
private const val MASK_KEY_VISIBLE_CHARS: Int = 4

public class Transport(
    public val baseUrl: HttpUrl,
    public val apiKey: String? = null,
    public val maxRetries: Int = 2,
    public val okHttpClient: OkHttpClient = createDefaultOkHttpClient(),
    public val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    public constructor(
        baseUrl: String,
        apiKey: String? = null,
        maxRetries: Int = 2,
        okHttpClient: OkHttpClient = createDefaultOkHttpClient(),
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        baseUrl = baseUrl.toHttpUrl(),
        apiKey = apiKey,
        maxRetries = maxRetries,
        okHttpClient = okHttpClient,
        ioDispatcher = ioDispatcher,
    )

    private val atomicRateLimit: AtomicReference<RateLimit?> = AtomicReference(null)

    public val rateLimit: RateLimit?
        get() = atomicRateLimit.get()

    public fun urlFor(
        path: String,
        queryParams: Map<String, Any?> = emptyMap(),
    ): HttpUrl {
        val builder = baseUrl.newBuilder()
        val cleanPath = path.trimStart('/')
        if (cleanPath.isNotEmpty()) {
            for (segment in cleanPath.split('/')) {
                builder.addPathSegment(segment)
            }
        }
        for ((key, value) in queryParams) {
            if (value != null) {
                builder.addQueryParameter(key, value.toString())
            }
        }
        return builder.build()
    }

    private fun prepareRequest(request: Request): Request {
        val builder = request.newBuilder()
        if (request.header("User-Agent") == null) {
            builder.header("User-Agent", "actos-kotlin/${ActosVersion.VERSION}")
        }
        if (request.header("Accept") == null) {
            builder.header("Accept", "application/json")
        }
        if (apiKey != null && request.header("Authorization") == null) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        return builder.build()
    }

    private fun isNonIdempotentPost(request: Request): Boolean =
        request.method.equals("POST", ignoreCase = true) && request.header("Idempotency-Key") == null

    private fun shouldRetryException(
        request: Request,
        attempt: Int,
    ): Boolean = attempt < maxRetries && !isNonIdempotentPost(request)

    private fun shouldRetryResponse(
        request: Request,
        response: Response,
        attempt: Int,
    ): Boolean {
        if (attempt >= maxRetries) return false
        val code = response.code
        return when {
            code == HTTP_TOO_MANY_REQUESTS -> true
            code in HTTP_CLIENT_ERROR_MIN..HTTP_CLIENT_ERROR_MAX -> false
            code in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX -> !isNonIdempotentPost(request)
            else -> false
        }
    }

    private fun calculateBackoffDelay(
        attempt: Int,
        retryAfterSeconds: Long?,
    ): Long {
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            return retryAfterSeconds * MILLIS_PER_SECOND
        }
        val baseDelay = BASE_BACKOFF_MS * (1L shl attempt)
        val capped = min(MAX_BACKOFF_MS, baseDelay)
        return Random.nextLong(0, capped + 1)
    }

    public suspend fun execute(
        request: Request,
        throwOnError: Boolean = true,
    ): Response =
        withContext(ioDispatcher) {
            val preparedRequest = prepareRequest(request)
            executeAttempt(preparedRequest, attempt = 0, throwOnError = throwOnError)
        }

    private suspend fun performHttpCall(
        request: Request,
        attempt: Int,
    ): Response {
        try {
            return okHttpClient.newCall(request).execute()
        } catch (e: java.net.SocketTimeoutException) {
            if (shouldRetryException(request, attempt)) {
                val delayMs = calculateBackoffDelay(attempt, retryAfterSeconds = null)
                delay(delayMs)
                return performHttpCall(request, attempt + 1)
            }
            throw ApiTimeoutException(e.message ?: "Request timed out", e)
        } catch (e: IOException) {
            if (shouldRetryException(request, attempt)) {
                val delayMs = calculateBackoffDelay(attempt, retryAfterSeconds = null)
                delay(delayMs)
                return performHttpCall(request, attempt + 1)
            }
            throw ApiConnectionException(e.message ?: "Connection failed", e)
        }
    }

    private suspend fun executeAttempt(
        request: Request,
        attempt: Int,
        throwOnError: Boolean,
    ): Response {
        val response = performHttpCall(request, attempt)

        val parsedRateLimit = RateLimit.fromHeaders(response.headers)
        if (parsedRateLimit != null) {
            atomicRateLimit.set(parsedRateLimit)
        }

        if (shouldRetryResponse(request, response, attempt)) {
            val retryAfterSeconds = parsedRateLimit?.retryAfter
            val delayMs = calculateBackoffDelay(attempt, retryAfterSeconds)
            response.close()
            delay(delayMs)
            return executeAttempt(request, attempt + 1, throwOnError)
        }

        if (!response.isSuccessful && throwOnError) {
            val bodyString = response.body?.string()
            response.close()
            throw createApiException(response, bodyString)
        }

        return response
    }

    public suspend fun get(
        path: String,
        queryParams: Map<String, Any?> = emptyMap(),
        headers: Headers = Headers.headersOf(),
    ): Response {
        val url = urlFor(path, queryParams)
        val request =
            Request.Builder()
                .url(url)
                .headers(headers)
                .get()
                .build()
        return execute(request)
    }

    public suspend fun post(
        path: String,
        body: RequestBody? = null,
        queryParams: Map<String, Any?> = emptyMap(),
        headers: Headers = Headers.headersOf(),
        idempotencyKey: String? = UUID.randomUUID().toString(),
    ): Response {
        val url = urlFor(path, queryParams)
        val requestBuilder =
            Request.Builder()
                .url(url)
                .headers(headers)
                .post(body ?: ByteArray(0).toRequestBody(null))

        if (idempotencyKey != null) {
            requestBuilder.header("Idempotency-Key", idempotencyKey)
        }

        return execute(requestBuilder.build())
    }

    public suspend fun put(
        path: String,
        body: RequestBody? = null,
        queryParams: Map<String, Any?> = emptyMap(),
        headers: Headers = Headers.headersOf(),
    ): Response {
        val url = urlFor(path, queryParams)
        val request =
            Request.Builder()
                .url(url)
                .headers(headers)
                .put(body ?: ByteArray(0).toRequestBody(null))
                .build()
        return execute(request)
    }

    public suspend fun patch(
        path: String,
        body: RequestBody? = null,
        queryParams: Map<String, Any?> = emptyMap(),
        headers: Headers = Headers.headersOf(),
    ): Response {
        val url = urlFor(path, queryParams)
        val request =
            Request.Builder()
                .url(url)
                .headers(headers)
                .patch(body ?: ByteArray(0).toRequestBody(null))
                .build()
        return execute(request)
    }

    public suspend fun delete(
        path: String,
        body: RequestBody? = null,
        queryParams: Map<String, Any?> = emptyMap(),
        headers: Headers = Headers.headersOf(),
    ): Response {
        val url = urlFor(path, queryParams)
        val requestBuilder =
            Request.Builder()
                .url(url)
                .headers(headers)

        if (body != null) {
            requestBuilder.delete(body)
        } else {
            requestBuilder.delete()
        }

        return execute(requestBuilder.build())
    }

    override fun close() {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
        okHttpClient.cache?.close()
    }

    override fun toString(): String {
        val maskedKey =
            when {
                apiKey == null -> "null"
                apiKey.length <= MASK_KEY_THRESHOLD -> "…"
                else -> "${apiKey.take(MASK_KEY_VISIBLE_CHARS)}…${apiKey.takeLast(MASK_KEY_VISIBLE_CHARS)}"
            }
        return "Transport(baseUrl=$baseUrl, apiKey=$maskedKey, maxRetries=$maxRetries)"
    }

    public companion object {
        public const val DEFAULT_TIMEOUT_SECONDS: Long = 30L

        public fun createDefaultOkHttpClient(
            loggingInterceptor: HttpLoggingInterceptor? = null,
            connectionPool: ConnectionPool = ConnectionPool(),
        ): OkHttpClient {
            val builder =
                OkHttpClient.Builder()
                    .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .connectionPool(connectionPool)
                    .retryOnConnectionFailure(false)

            loggingInterceptor?.let {
                it.redactHeader("Authorization")
                builder.addInterceptor(it)
            }

            return builder.build()
        }
    }
}
