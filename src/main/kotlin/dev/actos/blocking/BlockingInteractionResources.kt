package dev.actos.blocking

import dev.actos.Page
import dev.actos.UploadSource
import dev.actos.model.AdminActionSummary
import dev.actos.model.BanSummary
import dev.actos.model.ContentSummary
import dev.actos.model.MarkAllReadResponse
import dev.actos.model.NotificationSummary
import dev.actos.model.ReportSummary
import dev.actos.model.UploadResponse
import dev.actos.model.VoteResponse
import dev.actos.resources.AdminActionsResource
import dev.actos.resources.AdminBansResource
import dev.actos.resources.AdminContentsResource
import dev.actos.resources.AdminReportsResource
import dev.actos.resources.AdminResource
import dev.actos.resources.AdminRolesResource
import dev.actos.resources.InboxPage
import dev.actos.resources.InboxResource
import dev.actos.resources.MetaResource
import dev.actos.resources.MetaVersion
import dev.actos.resources.ReportsResource
import dev.actos.resources.SavesResource
import dev.actos.resources.UploadsResource
import dev.actos.resources.VotesResource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.io.InputStream

public class BlockingVotesResource internal constructor(
    private val delegate: VotesResource,
) {
    public fun set(
        contentId: String,
        value: Int,
    ): VoteResponse = runBlocking { delegate.set(contentId, value) }

    public fun up(contentId: String): VoteResponse = runBlocking { delegate.up(contentId) }

    public fun down(contentId: String): VoteResponse = runBlocking { delegate.down(contentId) }

    public fun clear(contentId: String): VoteResponse = runBlocking { delegate.clear(contentId) }

    @JvmOverloads
    public fun list(contentIds: List<String>? = null): Map<String, Int> =
        runBlocking {
            delegate.list(contentIds)
        }
}

public class BlockingSavesResource internal constructor(
    private val delegate: SavesResource,
) {
    public fun add(contentId: String): Unit = runBlocking { delegate.add(contentId) }

    public fun remove(contentId: String): Unit = runBlocking { delegate.remove(contentId) }

    @JvmOverloads
    public fun list(
        limit: Int? = null,
        cursor: String? = null,
        fields: List<String>? = null,
    ): Page<ContentSummary> =
        runBlocking {
            delegate.list(limit, cursor, fields)
        }

    @JvmOverloads
    public fun stream(
        limit: Int? = null,
        fields: List<String>? = null,
    ): Iterable<ContentSummary> = delegate.stream(limit, fields).toBlockingIterable()
}

public class BlockingUploadsResource internal constructor(
    private val delegate: UploadsResource,
) {
    public fun create(source: UploadSource): UploadResponse = runBlocking { delegate.create(source) }

    public fun create(file: File): UploadResponse = runBlocking { delegate.create(file) }

    @JvmOverloads
    public fun create(
        bytes: ByteArray,
        filename: String = "upload.bin",
    ): UploadResponse =
        runBlocking {
            delegate.create(bytes, filename)
        }

    @JvmOverloads
    public fun create(
        stream: InputStream,
        filename: String = "upload.bin",
        byteLength: Long? = null,
    ): UploadResponse =
        runBlocking {
            delegate.create(stream, filename, byteLength)
        }

    public fun delete(id: String): Unit = runBlocking { delegate.delete(id) }
}

public class BlockingReportsResource internal constructor(
    private val delegate: ReportsResource,
) {
    public fun create(
        targetType: String,
        targetId: String,
        reason: String,
    ): ReportSummary =
        runBlocking {
            delegate.create(targetType, targetId, reason)
        }
}

public class BlockingAdminResource internal constructor(
    private val delegate: AdminResource,
) {
    private val reportsResource by lazy { BlockingAdminReportsResource(delegate.reports()) }
    private val contentsResource by lazy { BlockingAdminContentsResource(delegate.contents()) }
    private val bansResource by lazy { BlockingAdminBansResource(delegate.bans()) }
    private val rolesResource by lazy { BlockingAdminRolesResource(delegate.roles()) }
    private val actionsResource by lazy { BlockingAdminActionsResource(delegate.actions()) }

    public fun reports(): BlockingAdminReportsResource = reportsResource

    public fun contents(): BlockingAdminContentsResource = contentsResource

    public fun bans(): BlockingAdminBansResource = bansResource

    public fun roles(): BlockingAdminRolesResource = rolesResource

    public fun actions(): BlockingAdminActionsResource = actionsResource
}

public class BlockingAdminReportsResource internal constructor(
    private val delegate: AdminReportsResource,
) {
    @JvmOverloads
    public fun list(
        status: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<ReportSummary> =
        runBlocking {
            delegate.list(status, limit, cursor)
        }

    @JvmOverloads
    public fun stream(
        status: String? = null,
        limit: Int? = null,
    ): Iterable<ReportSummary> = delegate.stream(status, limit).toBlockingIterable()

    @JvmOverloads
    public fun update(
        id: String,
        status: String,
        notes: String? = null,
    ): ReportSummary =
        runBlocking {
            delegate.update(id, status, notes)
        }
}

public class BlockingAdminContentsResource internal constructor(
    private val delegate: AdminContentsResource,
) {
    public fun delete(
        id: String,
        reason: String,
    ): Unit =
        runBlocking {
            delegate.delete(id, reason)
        }
}

public class BlockingAdminBansResource internal constructor(
    private val delegate: AdminBansResource,
) {
    @JvmOverloads
    public fun create(
        username: String,
        reason: String,
        expiresAt: String? = null,
    ): BanSummary =
        runBlocking {
            delegate.create(username, reason, expiresAt)
        }

    public fun remove(username: String): Unit = runBlocking { delegate.remove(username) }
}

public class BlockingAdminRolesResource internal constructor(
    private val delegate: AdminRolesResource,
) {
    public fun set(
        username: String,
        role: String?,
    ): Unit =
        runBlocking {
            delegate.set(username, role)
        }
}

public class BlockingAdminActionsResource internal constructor(
    private val delegate: AdminActionsResource,
) {
    @JvmOverloads
    public fun list(
        limit: Int? = null,
        cursor: String? = null,
    ): Page<AdminActionSummary> =
        runBlocking {
            delegate.list(limit, cursor)
        }

    @JvmOverloads
    public fun stream(limit: Int? = null): Iterable<AdminActionSummary> = delegate.stream(limit).toBlockingIterable()
}

public class BlockingInboxResource internal constructor(
    private val delegate: InboxResource,
) {
    @JvmOverloads
    public fun list(
        unread: Boolean = false,
        limit: Int? = null,
        cursor: String? = null,
    ): InboxPage =
        runBlocking {
            delegate.list(unread, limit, cursor)
        }

    @JvmOverloads
    public fun stream(
        unread: Boolean = false,
        limit: Int? = null,
    ): Iterable<NotificationSummary> = delegate.stream(unread, limit).toBlockingIterable()

    public fun read(notificationId: String): NotificationSummary = runBlocking { delegate.read(notificationId) }

    @JvmOverloads
    public fun readAll(upToCursor: String? = null): MarkAllReadResponse =
        runBlocking {
            delegate.readAll(upToCursor)
        }

    public fun unreadCount(): Int = runBlocking { delegate.unreadCount() }
}

public class BlockingMetaResource internal constructor(
    private val delegate: MetaResource,
) {
    public fun health(): JsonElement = runBlocking { delegate.health() }

    public fun ready(): JsonElement = runBlocking { delegate.ready() }

    public fun version(): MetaVersion = runBlocking { delegate.version() }

    public fun openapi(): JsonElement = runBlocking { delegate.openapi() }
}
