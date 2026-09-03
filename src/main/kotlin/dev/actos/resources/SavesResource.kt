package dev.actos.resources

import dev.actos.Page
import dev.actos.Transport
import dev.actos.model.ContentSummary
import dev.actos.model.SaveListResponse
import dev.actos.paginateFlow
import kotlinx.coroutines.flow.Flow

public class SavesResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Saves (bookmarks) a post or comment to the user's saved list.
     *
     * Idempotent: repeated calls succeed cleanly with 204 No Content.
     *
     * @param contentId The ID of the post or comment.
     */
    public suspend fun add(contentId: String) {
        transport.put("/contents/$contentId/save")
    }

    /**
     * Removes a post or comment from the user's saved list.
     *
     * Idempotent: repeated calls succeed cleanly with 204 No Content.
     *
     * @param contentId The ID of the post or comment.
     */
    public suspend fun remove(contentId: String) {
        transport.delete("/contents/$contentId/save")
    }

    /**
     * Lists saved posts and comments in reverse chronological order of when they were saved.
     *
     * @param limit Maximum items per page.
     * @param cursor Pagination cursor.
     * @param fields Optional list of field names for sparse fieldset filtering.
     * @return [Page] of [ContentSummary].
     */
    public suspend fun list(
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> {
        val queryParams =
            mapOf(
                "limit" to limit,
                "cursor" to cursor,
                "fields" to fields?.joinToString(","),
            )
        val response = transport.get("/me/saves", queryParams = queryParams)
        val parsed: SaveListResponse = response.parseJson()
        return Page(items = parsed.saves, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming saved items across pages.
     */
    public fun stream(
        limit: Int? = null,
        fields: List<String>? = null,
    ): Flow<ContentSummary> =
        paginateFlow { cursor ->
            list(limit = limit, cursor = cursor, fields = fields)
        }
}
