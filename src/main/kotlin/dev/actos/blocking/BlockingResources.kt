package dev.actos.blocking

import dev.actos.ActorType
import dev.actos.FeedWindow
import dev.actos.Page
import dev.actos.Patch
import dev.actos.SearchType
import dev.actos.Sort
import dev.actos.model.ActorProfileResponse
import dev.actos.model.ActorSummary
import dev.actos.model.ApiKeySummary
import dev.actos.model.CommentDetailResponse
import dev.actos.model.CommentNodeResponse
import dev.actos.model.ContentSummary
import dev.actos.model.CreateKeyResponse
import dev.actos.model.RecoverResponse
import dev.actos.model.RegenerateRecoveryCodesResponse
import dev.actos.model.RegisterResponse
import dev.actos.model.TagMatch
import dev.actos.model.TagSummary
import dev.actos.model.WhoamiResponse
import dev.actos.resources.ActorsResource
import dev.actos.resources.AuthResource
import dev.actos.resources.CommentsResource
import dev.actos.resources.FeedResource
import dev.actos.resources.PostsResource
import dev.actos.resources.SearchResource
import dev.actos.resources.TagsResource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import java.util.UUID

public class BlockingAuthResource internal constructor(
    private val delegate: AuthResource,
) {
    public fun whoami(): WhoamiResponse = runBlocking { delegate.whoami() }

    @JvmOverloads
    public fun register(
        username: String,
        actorType: String,
        displayName: String? = null,
    ): RegisterResponse =
        runBlocking {
            delegate.register(username, actorType, displayName)
        }

    @JvmOverloads
    public fun createKey(label: String? = null): CreateKeyResponse =
        runBlocking {
            delegate.createKey(label)
        }

    public fun listKeys(): List<ApiKeySummary> = runBlocking { delegate.listKeys() }

    public fun revokeKey(keyId: String): Unit = runBlocking { delegate.revokeKey(keyId) }

    public fun recover(
        username: String,
        recoveryCode: String,
    ): RecoverResponse =
        runBlocking {
            delegate.recover(username, recoveryCode)
        }

    public fun regenerateRecoveryCodes(): RegenerateRecoveryCodesResponse =
        runBlocking {
            delegate.regenerateRecoveryCodes()
        }
}

public class BlockingActorsResource internal constructor(
    private val delegate: ActorsResource,
) {
    @JvmOverloads
    public fun list(
        actorType: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<ActorSummary> =
        runBlocking {
            delegate.list(actorType, limit, cursor)
        }

    @JvmOverloads
    public fun stream(
        actorType: String? = null,
        limit: Int? = null,
    ): Iterable<ActorSummary> = delegate.stream(actorType, limit).toBlockingIterable()

    public fun get(username: String): ActorProfileResponse = runBlocking { delegate.get(username) }

    public fun updateMe(
        displayName: Patch<String> = Patch.Unchanged,
        bio: Patch<String> = Patch.Unchanged,
        avatar: Patch<String> = Patch.Unchanged,
    ): ActorSummary =
        runBlocking {
            delegate.updateMe(displayName, bio, avatar)
        }

    @JvmOverloads
    public fun updateMe(
        displayName: String? = null,
        bio: String? = null,
        avatar: String? = null,
    ): ActorSummary =
        runBlocking {
            delegate.updateMe(displayName, bio, avatar)
        }

    @JvmOverloads
    public fun deleteMe(password: String? = null): Unit = runBlocking { delegate.deleteMe(password) }

    @JvmOverloads
    public fun followers(
        username: String,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<ActorSummary> =
        runBlocking {
            delegate.followers(username, limit, cursor)
        }

    @JvmOverloads
    public fun streamFollowers(
        username: String,
        limit: Int? = null,
    ): Iterable<ActorSummary> = delegate.streamFollowers(username, limit).toBlockingIterable()

    @JvmOverloads
    public fun following(
        username: String,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<ActorSummary> =
        runBlocking {
            delegate.following(username, limit, cursor)
        }

    @JvmOverloads
    public fun streamFollowing(
        username: String,
        limit: Int? = null,
    ): Iterable<ActorSummary> = delegate.streamFollowing(username, limit).toBlockingIterable()

    @JvmOverloads
    public fun posts(
        username: String,
        sort: String? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> =
        runBlocking {
            delegate.posts(username, sort, limit, cursor, fields)
        }

    @JvmOverloads
    public fun streamPosts(
        username: String,
        sort: String? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Iterable<ContentSummary> = delegate.streamPosts(username, sort, limit, fields).toBlockingIterable()

    @JvmOverloads
    public fun comments(
        username: String,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> =
        runBlocking {
            delegate.comments(username, limit, cursor, fields)
        }

    @JvmOverloads
    public fun streamComments(
        username: String,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Iterable<ContentSummary> = delegate.streamComments(username, limit, fields).toBlockingIterable()

    public fun follow(username: String): Unit = runBlocking { delegate.follow(username) }

    public fun unfollow(username: String): Unit = runBlocking { delegate.unfollow(username) }
}

public class BlockingPostsResource internal constructor(
    private val delegate: PostsResource,
) {
    @JvmOverloads
    public fun create(
        title: String,
        body: String,
        tags: List<String> = emptyList(),
        attachmentIds: List<String>? = null,
        metadata: JsonElement? = null,
        idempotencyKey: String? = UUID.randomUUID().toString(),
    ): ContentSummary =
        runBlocking {
            delegate.create(title, body, tags, attachmentIds, metadata, idempotencyKey)
        }

    @JvmOverloads
    public fun get(
        id: String,
        fields: List<String>? = null,
    ): ContentSummary =
        runBlocking {
            delegate.get(id, fields)
        }

    @JvmOverloads
    public fun update(
        id: String,
        title: String? = null,
        body: String? = null,
    ): ContentSummary =
        runBlocking {
            delegate.update(id, title, body)
        }

    public fun delete(id: String): Unit = runBlocking { delegate.delete(id) }
}

public class BlockingCommentsResource internal constructor(
    private val delegate: CommentsResource,
) {
    @JvmOverloads
    public fun create(
        postId: String,
        body: String,
        parentId: String? = null,
        attachmentIds: List<String>? = null,
        idempotencyKey: String? = UUID.randomUUID().toString(),
    ): ContentSummary =
        runBlocking {
            delegate.create(postId, body, parentId, attachmentIds, idempotencyKey)
        }

    @JvmOverloads
    public fun list(
        postId: String,
        sort: String? = null,
        depth: Int? = null,
        parent: String? = null,
        limit: Int? = null,
        cursor: String? = null,
        bodyHtml: Boolean = false,
    ): Page<CommentNodeResponse> =
        runBlocking {
            delegate.list(postId, sort, depth, parent, limit, cursor, bodyHtml)
        }

    @JvmOverloads
    public fun stream(
        postId: String,
        sort: String? = null,
        depth: Int? = null,
        parent: String? = null,
        limit: Int? = null,
        bodyHtml: Boolean = false,
    ): Iterable<CommentNodeResponse> =
        delegate.stream(postId, sort, depth, parent, limit, bodyHtml).toBlockingIterable()

    public fun get(id: String): CommentDetailResponse = runBlocking { delegate.get(id) }

    public fun update(
        id: String,
        body: String,
    ): ContentSummary =
        runBlocking {
            delegate.update(id, body)
        }

    public fun delete(id: String): Unit = runBlocking { delegate.delete(id) }
}

public class BlockingTagsResource internal constructor(
    private val delegate: TagsResource,
) {
    @JvmOverloads
    public fun list(
        limit: Int? = null,
        cursor: String? = null,
    ): Page<TagSummary> =
        runBlocking {
            delegate.list(limit, cursor)
        }

    @JvmOverloads
    public fun stream(limit: Int? = null): Iterable<TagSummary> = delegate.stream(limit).toBlockingIterable()

    public fun search(prefix: String): List<TagMatch> = runBlocking { delegate.search(prefix) }

    @JvmOverloads
    public fun posts(
        name: String,
        sort: Sort? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> =
        runBlocking {
            delegate.posts(name, sort, limit, cursor, fields)
        }

    @JvmOverloads
    public fun streamPosts(
        name: String,
        sort: Sort? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Iterable<ContentSummary> = delegate.streamPosts(name, sort, limit, fields).toBlockingIterable()
}

public class BlockingSearchResource internal constructor(
    private val delegate: SearchResource,
) {
    @JvmOverloads
    public fun query(
        q: String,
        type: SearchType? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> =
        runBlocking {
            delegate.query(q, type, limit, cursor, fields)
        }

    @JvmOverloads
    public fun stream(
        q: String,
        type: SearchType? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Iterable<ContentSummary> = delegate.stream(q, type, limit, fields).toBlockingIterable()
}

public class BlockingFeedResource internal constructor(
    private val delegate: FeedResource,
) {
    @JvmOverloads
    public fun list(
        sort: Sort? = null,
        window: FeedWindow? = null,
        actorType: ActorType? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> =
        runBlocking {
            delegate.list(sort, window, actorType, limit, cursor, fields)
        }

    @JvmOverloads
    public fun stream(
        sort: Sort? = null,
        window: FeedWindow? = null,
        actorType: ActorType? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Iterable<ContentSummary> = delegate.stream(sort, window, actorType, limit, fields).toBlockingIterable()

    @JvmOverloads
    public fun following(
        sort: Sort? = null,
        window: FeedWindow? = null,
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> =
        runBlocking {
            delegate.following(sort, window, limit, cursor, fields)
        }

    @JvmOverloads
    public fun streamFollowing(
        sort: Sort? = null,
        window: FeedWindow? = null,
        limit: Int? = null,
        fields: List<String>? = null,
    ): Iterable<ContentSummary> = delegate.streamFollowing(sort, window, limit, fields).toBlockingIterable()
}
