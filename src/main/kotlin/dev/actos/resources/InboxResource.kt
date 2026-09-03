package dev.actos.resources

import dev.actos.Page
import dev.actos.RateLimitException
import dev.actos.Transport
import dev.actos.model.InboxResponse
import dev.actos.model.MarkAllReadResponse
import dev.actos.model.NotificationSummary
import dev.actos.paginateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Paginated response page for notifications in the user's inbox.
 *
 * @param items List of notifications on the current page.
 * @param nextCursor Cursor for the next page of notifications, or `null` if this is the final page.
 * @param unreadCount Total count of unread notifications across the account, not just on this page.
 */
public data class InboxPage(
    public val items: List<NotificationSummary>,
    public val nextCursor: String?,
    public val unreadCount: Int,
)

@Serializable
private data class MarkAllReadPayload(
    @SerialName("up_to_cursor")
    val upToCursor: String? = null,
)

public class InboxResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Lists notifications for the authenticated user.
     *
     * **TARGET TYPE & DELETION SEMANTICS NOTE**:
     * - `targetType` returns `"content"` for both posts and comments (they share the same ID space).
     *   The distinction is indicated by the `kind` field.
     * - If the target was soft-deleted, fetching a target post returns `410 Gone` (throwing
     *   [dev.actos.GoneException]), while fetching a target comment returns `200 OK` with
     *   `deleted = true` (to preserve thread integrity).
     *
     * @param unread When `true`, filters to only unread notifications.
     * @param limit Maximum notifications per page.
     * @param cursor Pagination cursor.
     * @return [InboxPage] containing notifications and the total account-wide unread count.
     */
    public suspend fun list(
        unread: Boolean = false,
        limit: Int? = null,
        cursor: String? = null,
    ): InboxPage {
        val queryParams =
            mapOf(
                "unread" to if (unread) "true" else null,
                "limit" to limit,
                "cursor" to cursor,
            )
        val response = transport.get("/me/inbox", queryParams = queryParams)
        val parsed: InboxResponse = response.parseJson()
        return InboxPage(
            items = parsed.notifications,
            nextCursor = parsed.nextCursor,
            unreadCount = parsed.unreadCount.toInt(),
        )
    }

    /**
     * Returns a cold [Flow] streaming notifications across pages.
     */
    public fun stream(
        unread: Boolean = false,
        limit: Int? = null,
    ): Flow<NotificationSummary> =
        paginateFlow { cursor ->
            val page = list(unread = unread, limit = limit, cursor = cursor)
            Page(items = page.items, nextCursor = page.nextCursor)
        }

    /**
     * Marks a single notification as read.
     *
     * @param notificationId The ID of the notification.
     * @return Updated [NotificationSummary] with `readAt` populated.
     */
    public suspend fun read(notificationId: String): NotificationSummary {
        val response = transport.post("/me/inbox/$notificationId/read")
        return response.parseJson()
    }

    /**
     * Marks all notifications (or up to an optional cursor) as read.
     *
     * Idempotent: repeated calls succeed without error.
     *
     * @param upToCursor Optional cursor limiting which notifications are marked read.
     * @return [MarkAllReadResponse] containing the count of newly marked notifications.
     */
    public suspend fun readAll(upToCursor: String? = null): MarkAllReadResponse {
        val payload = MarkAllReadPayload(upToCursor = upToCursor)
        val response = transport.post("/me/inbox/read", body = payload.toJsonRequestBody())
        return response.parseJson()
    }

    /**
     * Returns the total count of unread notifications across the account.
     *
     * Reads the `unread_count` field from a lightweight `list(limit = 1)` call.
     */
    public suspend fun unreadCount(): Int = list(limit = 1).unreadCount

    /**
     * Returns a cold [Flow] that periodically polls for new unread notifications.
     *
     * **NETWORK / BATTERY NOTICE**:
     * The Actos server does not provide Push or SSE mechanisms; this method uses polling.
     * On Android or background services, be mindful of battery usage and background limits.
     *
     * The polling loop cooperatively cancels when the enclosing coroutine scope is cancelled.
     * Adheres to server `Retry-After` headers and rate limits if encountered.
     *
     * @param interval Polling interval between requests (default: 10 seconds).
     */
    public fun watch(interval: Duration = 10.seconds): Flow<NotificationSummary> =
        flow {
            val seenIds = mutableSetOf<String>()
            var initialFetch = true

            while (currentCoroutineContext().isActive) {
                try {
                    val page = list(unread = true, limit = 20)
                    for (item in page.items) {
                        if (item.id !in seenIds) {
                            seenIds.add(item.id)
                            if (!initialFetch) {
                                emit(item)
                            }
                        }
                    }
                    initialFetch = false
                    delay(interval)
                } catch (e: RateLimitException) {
                    val waitDuration = e.retryAfter?.seconds ?: interval
                    delay(waitDuration)
                }
            }
        }
}
