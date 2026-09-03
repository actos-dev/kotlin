package dev.actos.blocking

import dev.actos.Actos
import dev.actos.RateLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.Closeable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Synchronous, blocking facade for the Actos SDK designed for Java consumers and blocking JVM environments.
 *
 * **ANDROID THREADING WARNING**:
 * Blocking methods execute synchronously using `runBlocking` and must **NEVER** be called on the
 * Android Main/UI thread. Calling blocking methods on the main thread will trigger
 * `NetworkOnMainThreadException` or cause Application Not Responding (ANR) hangs.
 * On Android, use the asynchronous [Actos] client with coroutines or dispatch blocking calls
 * on a background thread pool (e.g. `Executors.newCachedThreadPool()`).
 */
public class BlockingActos(
    public val delegate: Actos,
) : Closeable, AutoCloseable {
    @JvmOverloads
    public constructor(
        baseUrl: String = Actos.DEFAULT_BASE_URL,
        apiKey: String? = null,
        timeout: Duration = 30.seconds,
        maxRetries: Int = 3,
        okHttpClient: OkHttpClient? = null,
    ) : this(
        Actos(
            baseUrl = baseUrl,
            apiKey = apiKey,
            timeout = timeout,
            maxRetries = maxRetries,
            okHttpClient = okHttpClient,
        ),
    )

    private val authResource by lazy { BlockingAuthResource(delegate.auth()) }
    private val actorsResource by lazy { BlockingActorsResource(delegate.actors()) }
    private val postsResource by lazy { BlockingPostsResource(delegate.posts()) }
    private val commentsResource by lazy { BlockingCommentsResource(delegate.comments()) }
    private val tagsResource by lazy { BlockingTagsResource(delegate.tags()) }
    private val searchResource by lazy { BlockingSearchResource(delegate.search()) }
    private val feedResource by lazy { BlockingFeedResource(delegate.feed()) }
    private val votesResource by lazy { BlockingVotesResource(delegate.votes()) }
    private val savesResource by lazy { BlockingSavesResource(delegate.saves()) }
    private val uploadsResource by lazy { BlockingUploadsResource(delegate.uploads()) }
    private val reportsResource by lazy { BlockingReportsResource(delegate.reports()) }
    private val adminResource by lazy { BlockingAdminResource(delegate.admin()) }
    private val inboxResource by lazy { BlockingInboxResource(delegate.inbox()) }
    private val metaResource by lazy { BlockingMetaResource(delegate.meta()) }

    public val rateLimit: RateLimit?
        get() = delegate.rateLimit

    public fun auth(): BlockingAuthResource = authResource

    public fun actors(): BlockingActorsResource = actorsResource

    public fun posts(): BlockingPostsResource = postsResource

    public fun comments(): BlockingCommentsResource = commentsResource

    public fun tags(): BlockingTagsResource = tagsResource

    public fun search(): BlockingSearchResource = searchResource

    public fun feed(): BlockingFeedResource = feedResource

    public fun votes(): BlockingVotesResource = votesResource

    public fun saves(): BlockingSavesResource = savesResource

    public fun uploads(): BlockingUploadsResource = uploadsResource

    public fun reports(): BlockingReportsResource = reportsResource

    public fun admin(): BlockingAdminResource = adminResource

    public fun inbox(): BlockingInboxResource = inboxResource

    public fun meta(): BlockingMetaResource = metaResource

    override fun close() {
        delegate.close()
    }

    public companion object {
        /**
         * Creates a [BlockingActos] configured from environment variables (`ACTOS_API_KEY` and `ACTOS_BASE_URL`).
         */
        @JvmStatic
        public fun fromEnv(): BlockingActos = Actos.fromEnv().asBlocking()
    }
}

/**
 * Returns a [BlockingActos] facade wrapping this asynchronous [Actos] client.
 */
public fun Actos.asBlocking(): BlockingActos = BlockingActos(this)

internal fun <T> Flow<T>.toBlockingIterable(): Iterable<T> =
    Iterable {
        runBlocking {
            toList().iterator()
        }
    }
