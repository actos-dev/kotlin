package dev.actos.resources

import dev.actos.Page
import dev.actos.SearchType
import dev.actos.Transport
import dev.actos.model.ContentSearchResponse
import dev.actos.model.ContentSummary
import dev.actos.paginateFlow
import kotlinx.coroutines.flow.Flow

public class SearchResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Performs full-text search across posts, comments, or actors.
     *
     * Cursor pagination is meaningful only when querying with the identical search term `q`.
     *
     * @param q The search query string.
     * @param type Content type filter (`post`, `comment`, or `actor`). Defaults to posts.
     * @param limit Maximum results per page.
     * @param cursor Pagination cursor from previous page.
     * @param fields Optional list of field names for sparse fieldset filtering.
     * @return [Page] of [ContentSummary].
     */
    public suspend fun query(
        q: String,
        type: SearchType? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> {
        val queryParams =
            mapOf(
                "q" to q,
                "type" to type?.value,
                "limit" to limit,
                "cursor" to cursor,
                "fields" to fields?.joinToString(","),
            )
        val response = transport.get("/search", queryParams = queryParams)
        val parsed: ContentSearchResponse = response.parseJson()
        return Page(items = parsed.results, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming search results across pages.
     */
    public fun stream(
        q: String,
        type: SearchType? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Flow<ContentSummary> =
        paginateFlow { cursor ->
            query(q = q, type = type, limit = limit, cursor = cursor, fields = fields)
        }
}
