package dev.actos.resources

import dev.actos.Page
import dev.actos.Sort
import dev.actos.Transport
import dev.actos.model.ContentSummary
import dev.actos.model.PostListResponse
import dev.actos.model.TagListResponse
import dev.actos.model.TagMatch
import dev.actos.model.TagSearchResponse
import dev.actos.model.TagSummary
import dev.actos.paginateFlow
import kotlinx.coroutines.flow.Flow

public class TagsResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Lists popular tags in descending order of post count.
     *
     * Note: The `/tags` discovery endpoint does NOT support `fields`.
     */
    public suspend fun list(
        limit: Int? = null,
        cursor: String? = null,
    ): Page<TagSummary> {
        val queryParams =
            mapOf(
                "limit" to limit,
                "cursor" to cursor,
            )
        val response = transport.get("/tags", queryParams = queryParams)
        val parsed: TagListResponse = response.parseJson()
        return Page(items = parsed.tags, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming popular tags across pages.
     */
    public fun stream(limit: Int? = null): Flow<TagSummary> =
        paginateFlow { cursor ->
            list(limit = limit, cursor = cursor)
        }

    /**
     * Autocompletes tag names by prefix.
     *
     * Results are capped at the server limit with no pagination.
     *
     * @param prefix The tag prefix to search for.
     * @return List of matching tags.
     */
    public suspend fun search(prefix: String): List<TagMatch> {
        val queryParams = mapOf("q" to prefix)
        val response = transport.get("/tags/search", queryParams = queryParams)
        val parsed: TagSearchResponse = response.parseJson()
        return parsed.tags
    }

    /**
     * Lists posts tagged with the given tag name.
     *
     * @param name The tag name (without `#`).
     * @param sort Sort order.
     * @param limit Items per page.
     * @param cursor Pagination cursor.
     * @param fields Optional list of field names for sparse fieldset filtering.
     * @return [Page] of [ContentSummary].
     */
    public suspend fun posts(
        name: String,
        sort: Sort? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> {
        val queryParams =
            mapOf(
                "sort" to sort?.value,
                "limit" to limit,
                "cursor" to cursor,
                "fields" to fields?.joinToString(","),
            )
        val response = transport.get("/tags/$name/posts", queryParams = queryParams)
        val parsed: PostListResponse = response.parseJson()
        return Page(items = parsed.posts, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming posts tagged with the given tag name across pages.
     */
    public fun streamPosts(
        name: String,
        sort: Sort? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Flow<ContentSummary> =
        paginateFlow { cursor ->
            posts(name = name, sort = sort, limit = limit, cursor = cursor, fields = fields)
        }
}
