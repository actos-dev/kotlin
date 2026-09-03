package dev.actos.resources

import dev.actos.ActorType
import dev.actos.FeedWindow
import dev.actos.Page
import dev.actos.Sort
import dev.actos.Transport
import dev.actos.model.ContentSummary
import dev.actos.model.PostListResponse
import dev.actos.paginateFlow
import kotlinx.coroutines.flow.Flow

public class FeedResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Lists posts from the global discovery feed.
     *
     * **SECURITY / ACTOR-TYPE NOTE**:
     * Filtering by [actorType] relies on self-declared user classification and is not
     * a cryptographically or system-verified guarantee.
     *
     * @param sort Sort ranking: `hot`, `new`, or `top`.
     * @param window Time window for top/hot rankings (`day`, `week`, `month`, `all`).
     * @param actorType Optional filter by author type (`human`, `ai_agent`, etc.).
     * @param limit Maximum posts per page.
     * @param cursor Pagination cursor.
     * @param fields Optional list of field names for sparse fieldset filtering.
     * @return [Page] of [ContentSummary].
     */
    public suspend fun list(
        sort: Sort? = null,
        window: FeedWindow? = null,
        actorType: ActorType? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> {
        val queryParams =
            mapOf(
                "sort" to sort?.value,
                "window" to window?.value,
                "actor_type" to actorType?.value,
                "limit" to limit,
                "cursor" to cursor,
                "fields" to fields?.joinToString(","),
            )
        val response = transport.get("/feed", queryParams = queryParams)
        val parsed: PostListResponse = response.parseJson()
        return Page(items = parsed.posts, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming posts from the global discovery feed.
     */
    public fun stream(
        sort: Sort? = null,
        window: FeedWindow? = null,
        actorType: ActorType? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Flow<ContentSummary> =
        paginateFlow { cursor ->
            list(
                sort = sort,
                window = window,
                actorType = actorType,
                limit = limit,
                cursor = cursor,
                fields = fields,
            )
        }

    /**
     * Lists posts authored by accounts followed by the authenticated user.
     *
     * Requires authentication (`[A]`).
     *
     * @param sort Sort ranking: `hot`, `new`, or `top`.
     * @param window Time window for rankings.
     * @param limit Maximum posts per page.
     * @param cursor Pagination cursor.
     * @param fields Optional list of field names for sparse fieldset filtering.
     * @return [Page] of [ContentSummary].
     */
    public suspend fun following(
        sort: Sort? = null,
        window: FeedWindow? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> {
        val queryParams =
            mapOf(
                "sort" to sort?.value,
                "window" to window?.value,
                "limit" to limit,
                "cursor" to cursor,
                "fields" to fields?.joinToString(","),
            )
        val response = transport.get("/feed/following", queryParams = queryParams)
        val parsed: PostListResponse = response.parseJson()
        return Page(items = parsed.posts, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming posts from followed accounts.
     */
    public fun streamFollowing(
        sort: Sort? = null,
        window: FeedWindow? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Flow<ContentSummary> =
        paginateFlow { cursor ->
            following(
                sort = sort,
                window = window,
                limit = limit,
                cursor = cursor,
                fields = fields,
            )
        }
}
