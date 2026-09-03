package dev.actos.resources

import dev.actos.Page
import dev.actos.Patch
import dev.actos.Transport
import dev.actos.model.ActorListResponse
import dev.actos.model.ActorProfileResponse
import dev.actos.model.ActorSummary
import dev.actos.model.CommentListResponse
import dev.actos.model.ContentSummary
import dev.actos.model.DeleteAccountRequest
import dev.actos.model.PostListResponse
import dev.actos.paginateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.RequestBody.Companion.toRequestBody

public class ActorsResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Lists actors with optional filtering by actor type and cursor-based pagination.
     *
     * Note: Per Actos specification, the `/actors` discovery endpoint does NOT support `fields`.
     */
    public suspend fun list(
        actorType: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<ActorSummary> {
        val queryParams =
            mapOf(
                "actor_type" to actorType,
                "limit" to limit,
                "cursor" to cursor,
            )
        val response = transport.get("/actors", queryParams = queryParams)
        val parsed: ActorListResponse = response.parseJson()
        return Page(items = parsed.actors, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] that automatically iterates across pages of actors.
     */
    public fun stream(
        actorType: String? = null,
        limit: Int? = null,
    ): Flow<ActorSummary> =
        paginateFlow { cursor ->
            list(actorType = actorType, limit = limit, cursor = cursor)
        }

    /**
     * Retrieves an actor's profile and stats by username.
     *
     * @throws dev.actos.NotFoundException if actor does not exist (404).
     * @throws dev.actos.GoneException if actor account was deleted (410).
     */
    public suspend fun get(username: String): ActorProfileResponse {
        val response = transport.get("/actors/$username")
        return response.parseJson()
    }

    /**
     * Updates profile details of the authenticated actor using tri-state [Patch] semantics.
     *
     * - [Patch.Unchanged]: field is omitted from payload and left unmodified.
     * - [Patch.Clear]: field is set to `null` to clear its value (e.g. remove avatar).
     * - [Patch.Value]: field is updated with the given string.
     */
    public suspend fun updateMe(
        displayName: Patch<String> = Patch.Unchanged,
        bio: Patch<String> = Patch.Unchanged,
        avatar: Patch<String> = Patch.Unchanged,
    ): ActorSummary {
        val jsonMap =
            buildMap<String, JsonElement> {
                when (displayName) {
                    is Patch.Unchanged -> {}
                    is Patch.Clear -> put("display_name", JsonNull)
                    is Patch.Value -> put("display_name", JsonPrimitive(displayName.value))
                }
                when (bio) {
                    is Patch.Unchanged -> {}
                    is Patch.Clear -> put("bio", JsonNull)
                    is Patch.Value -> put("bio", JsonPrimitive(bio.value))
                }
                when (avatar) {
                    is Patch.Unchanged -> {}
                    is Patch.Clear -> put("avatar", JsonNull)
                    is Patch.Value -> put("avatar", JsonPrimitive(avatar.value))
                }
            }
        val body = JsonObject(jsonMap).toString().toRequestBody(JSON_MEDIA_TYPE)
        val response = transport.patch("/actors/me", body = body)
        return response.parseJson()
    }

    /**
     * Convenience overload for [updateMe] accepting nullable strings.
     */
    public suspend fun updateMe(
        displayName: String?,
        bio: String?,
        avatar: String?,
    ): ActorSummary =
        updateMe(
            displayName = Patch.of(displayName),
            bio = Patch.of(bio),
            avatar = Patch.of(avatar),
        )

    /**
     * Permanently deletes the authenticated actor's account.
     *
     * Requires valid recovery code confirmation.
     */
    public suspend fun deleteMe(recoveryCode: String? = null) {
        val body = recoveryCode?.let { DeleteAccountRequest(it).toJsonRequestBody() }
        transport.delete("/actors/me", body = body)
    }

    /**
     * Lists followers of the specified actor.
     */
    public suspend fun followers(
        username: String,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<ActorSummary> {
        val queryParams =
            mapOf(
                "limit" to limit,
                "cursor" to cursor,
            )
        val response = transport.get("/actors/$username/followers", queryParams = queryParams)
        val parsed: ActorListResponse = response.parseJson()
        return Page(items = parsed.actors, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming followers of the specified actor.
     */
    public fun streamFollowers(
        username: String,
        limit: Int? = null,
    ): Flow<ActorSummary> =
        paginateFlow { cursor ->
            followers(username = username, limit = limit, cursor = cursor)
        }

    /**
     * Lists accounts followed by the specified actor.
     */
    public suspend fun following(
        username: String,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<ActorSummary> {
        val queryParams =
            mapOf(
                "limit" to limit,
                "cursor" to cursor,
            )
        val response = transport.get("/actors/$username/following", queryParams = queryParams)
        val parsed: ActorListResponse = response.parseJson()
        return Page(items = parsed.actors, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming accounts followed by the specified actor.
     */
    public fun streamFollowing(
        username: String,
        limit: Int? = null,
    ): Flow<ActorSummary> =
        paginateFlow { cursor ->
            following(username = username, limit = limit, cursor = cursor)
        }

    /**
     * Lists posts created by the specified actor.
     *
     * @param fields Optional list of field names for sparse fieldset filtering.
     */
    public suspend fun posts(
        username: String,
        sort: String? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> {
        val queryParams =
            mapOf(
                "sort" to sort,
                "limit" to limit,
                "cursor" to cursor,
                "fields" to fields?.joinToString(","),
            )
        val response = transport.get("/actors/$username/posts", queryParams = queryParams)
        val parsed: PostListResponse = response.parseJson()
        return Page(items = parsed.posts, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming posts created by the specified actor.
     */
    public fun streamPosts(
        username: String,
        sort: String? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Flow<ContentSummary> =
        paginateFlow { cursor ->
            posts(username = username, sort = sort, limit = limit, cursor = cursor, fields = fields)
        }

    /**
     * Lists comments authored by the specified actor.
     */
    public suspend fun comments(
        username: String,
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
        val response = transport.get("/actors/$username/comments", queryParams = queryParams)
        val parsed: CommentListResponse = response.parseJson()
        return Page(items = parsed.comments, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming comments authored by the specified actor.
     */
    public fun streamComments(
        username: String,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Flow<ContentSummary> =
        paginateFlow { cursor ->
            comments(username = username, limit = limit, cursor = cursor, fields = fields)
        }

    /**
     * Follows the specified actor.
     *
     * Idempotent: multiple calls succeed without side-effects.
     */
    public suspend fun follow(username: String) {
        transport.put("/actors/$username/follow")
    }

    /**
     * Unfollows the specified actor.
     *
     * Idempotent: multiple calls succeed without side-effects.
     */
    public suspend fun unfollow(username: String) {
        transport.delete("/actors/$username/follow")
    }
}
