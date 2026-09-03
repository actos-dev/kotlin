package dev.actos.resources

import dev.actos.Transport
import dev.actos.model.VoteMapResponse
import dev.actos.model.VoteRequest
import dev.actos.model.VoteResponse

public class VotesResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Casts or updates a vote on a post or comment.
     *
     * Idempotent: repeated calls with the same value succeed without error.
     *
     * @param contentId The ID of the post or comment.
     * @param value `1` for upvote, `-1` for downvote, `0` to retract vote.
     * @return [VoteResponse] containing the updated score and user's vote value.
     */
    public suspend fun set(
        contentId: String,
        value: Int,
    ): VoteResponse {
        val request = VoteRequest(value = value)
        val response =
            transport.put(
                path = "/contents/$contentId/vote",
                body = request.toJsonRequestBody(),
            )
        return response.parseJson()
    }

    /**
     * Upvotes a piece of content (+1).
     *
     * Idempotent: repeated calls succeed without error.
     */
    public suspend fun up(contentId: String): VoteResponse = set(contentId, 1)

    /**
     * Downvotes a piece of content (-1).
     *
     * Idempotent: repeated calls succeed without error.
     */
    public suspend fun down(contentId: String): VoteResponse = set(contentId, -1)

    /**
     * Retracts an existing vote (sets vote to 0).
     *
     * Idempotent: repeated calls succeed without error.
     */
    public suspend fun clear(contentId: String): VoteResponse = set(contentId, 0)

    /**
     * Lists the authenticated user's active votes.
     *
     * Contents without an active vote are omitted from the resulting map.
     *
     * @param contentIds Optional list of specific content IDs to query.
     * @return [Map] of content ID to vote value (`1` or `-1`).
     */
    public suspend fun list(contentIds: List<String>? = null): Map<String, Int> {
        val queryParams =
            mapOf(
                "content_ids" to contentIds?.joinToString(","),
            )
        val response = transport.get("/me/votes", queryParams = queryParams)
        val parsed: VoteMapResponse = response.parseJson()
        return parsed.votes
    }
}
