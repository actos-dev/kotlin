package dev.actos

import dev.actos.resources.ActorsResource
import dev.actos.resources.AdminResource
import dev.actos.resources.AuthResource
import dev.actos.resources.CommentsResource
import dev.actos.resources.FeedResource
import dev.actos.resources.InboxResource
import dev.actos.resources.MetaResource
import dev.actos.resources.PostsResource
import dev.actos.resources.ReportsResource
import dev.actos.resources.SavesResource
import dev.actos.resources.SearchResource
import dev.actos.resources.TagsResource
import dev.actos.resources.UploadsResource
import dev.actos.resources.VotesResource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public object ActosVersion {
    public const val VERSION: String = "0.1.0"
}

public class Actos(
    public val apiKey: String? = null,
    public val baseUrl: String = DEFAULT_BASE_URL,
    public val timeout: Duration = DEFAULT_TIMEOUT,
    public val maxRetries: Int = DEFAULT_MAX_RETRIES,
    okHttpClient: OkHttpClient? = null,
) : Closeable {
    public val okHttpClient: OkHttpClient =
        okHttpClient ?: createConfiguredOkHttpClient(timeout)

    internal val transport: Transport =
        Transport(
            baseUrl = baseUrl,
            apiKey = apiKey,
            maxRetries = maxRetries,
            okHttpClient = this.okHttpClient,
        )

    public val rateLimit: RateLimit?
        get() = transport.rateLimit

    private val authResource: AuthResource by lazy { AuthResource(transport) }
    private val actorsResource: ActorsResource by lazy { ActorsResource(transport) }
    private val postsResource: PostsResource by lazy { PostsResource(transport) }
    private val commentsResource: CommentsResource by lazy { CommentsResource(transport) }
    private val tagsResource: TagsResource by lazy { TagsResource(transport) }
    private val searchResource: SearchResource by lazy { SearchResource(transport) }
    private val feedResource: FeedResource by lazy { FeedResource(transport) }
    private val votesResource: VotesResource by lazy { VotesResource(transport) }
    private val savesResource: SavesResource by lazy { SavesResource(transport) }
    private val uploadsResource: UploadsResource by lazy { UploadsResource(transport) }
    private val reportsResource: ReportsResource by lazy { ReportsResource(transport) }
    private val adminResource: AdminResource by lazy { AdminResource(transport) }
    private val inboxResource: InboxResource by lazy { InboxResource(transport) }
    private val metaResource: MetaResource by lazy { MetaResource(transport) }

    public fun auth(): AuthResource = authResource

    public fun actors(): ActorsResource = actorsResource

    public fun posts(): PostsResource = postsResource

    public fun comments(): CommentsResource = commentsResource

    public fun tags(): TagsResource = tagsResource

    public fun search(): SearchResource = searchResource

    public fun feed(): FeedResource = feedResource

    public fun votes(): VotesResource = votesResource

    public fun saves(): SavesResource = savesResource

    public fun uploads(): UploadsResource = uploadsResource

    public fun reports(): ReportsResource = reportsResource

    public fun admin(): AdminResource = adminResource

    public fun inbox(): InboxResource = inboxResource

    public fun meta(): MetaResource = metaResource

    public suspend fun request(
        method: String,
        path: String,
        body: RequestBody? = null,
        headers: Headers = Headers.headersOf(),
        queryParams: Map<String, Any?> = emptyMap(),
    ): Response {
        val url = transport.urlFor(path, queryParams)
        val request =
            Request.Builder()
                .url(url)
                .headers(headers)
                .method(method.uppercase(), body)
                .build()
        return transport.execute(request, throwOnError = true)
    }

    override fun close() {
        transport.close()
    }

    override fun toString(): String =
        "Actos(baseUrl=$baseUrl, apiKey=${maskKey(apiKey)}, timeout=$timeout, maxRetries=$maxRetries)"

    public class Builder {
        private var apiKey: String? = null
        private var baseUrl: String = DEFAULT_BASE_URL
        private var timeout: Duration = DEFAULT_TIMEOUT
        private var maxRetries: Int = DEFAULT_MAX_RETRIES
        private var okHttpClient: OkHttpClient? = null

        public fun apiKey(apiKey: String?): Builder =
            apply {
                this.apiKey = apiKey
            }

        public fun baseUrl(baseUrl: String): Builder =
            apply {
                this.baseUrl = baseUrl
            }

        public fun timeout(timeout: Duration): Builder =
            apply {
                this.timeout = timeout
            }

        public fun maxRetries(maxRetries: Int): Builder =
            apply {
                this.maxRetries = maxRetries
            }

        public fun okHttpClient(okHttpClient: OkHttpClient): Builder =
            apply {
                this.okHttpClient = okHttpClient
            }

        public fun build(): Actos =
            Actos(
                apiKey = apiKey,
                baseUrl = baseUrl,
                timeout = timeout,
                maxRetries = maxRetries,
                okHttpClient = okHttpClient,
            )
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "http://127.0.0.1:3100"
        public val DEFAULT_TIMEOUT: Duration = 30.seconds
        public const val DEFAULT_MAX_RETRIES: Int = 2
        private const val MASK_KEY_THRESHOLD: Int = 8
        private const val MASK_KEY_VISIBLE_CHARS: Int = 4

        public fun builder(): Builder = Builder()

        public fun fromEnv(): Actos {
            val envKey = System.getenv("ACTOS_API_KEY")
            val envBaseUrl = System.getenv("ACTOS_BASE_URL")?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
            return Actos(
                apiKey = envKey,
                baseUrl = envBaseUrl,
            )
        }

        private fun createConfiguredOkHttpClient(timeout: Duration): OkHttpClient {
            val timeoutMillis = timeout.inWholeMilliseconds
            return Transport.createDefaultOkHttpClient().newBuilder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build()
        }

        private fun maskKey(key: String?): String =
            when {
                key == null -> "null"
                key.length <= MASK_KEY_THRESHOLD -> "…"
                else -> "${key.take(MASK_KEY_VISIBLE_CHARS)}…${key.takeLast(MASK_KEY_VISIBLE_CHARS)}"
            }
    }
}
