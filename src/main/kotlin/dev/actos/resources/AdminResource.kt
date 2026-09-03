package dev.actos.resources

import dev.actos.Page
import dev.actos.Transport
import dev.actos.model.AdminActionListResponse
import dev.actos.model.AdminActionSummary
import dev.actos.model.BanSummary
import dev.actos.model.CreateBanRequest
import dev.actos.model.ModerateDeleteRequest
import dev.actos.model.ReportListResponse
import dev.actos.model.ReportSummary
import dev.actos.model.SetRoleRequest
import dev.actos.model.UpdateReportRequest
import dev.actos.paginateFlow
import kotlinx.coroutines.flow.Flow

public class AdminResource internal constructor(
    internal val transport: Transport,
) {
    private val reportsResource: AdminReportsResource by lazy { AdminReportsResource(transport) }
    private val contentsResource: AdminContentsResource by lazy { AdminContentsResource(transport) }
    private val bansResource: AdminBansResource by lazy { AdminBansResource(transport) }
    private val rolesResource: AdminRolesResource by lazy { AdminRolesResource(transport) }
    private val actionsResource: AdminActionsResource by lazy { AdminActionsResource(transport) }

    public fun reports(): AdminReportsResource = reportsResource

    public fun contents(): AdminContentsResource = contentsResource

    public fun bans(): AdminBansResource = bansResource

    public fun roles(): AdminRolesResource = rolesResource

    public fun actions(): AdminActionsResource = actionsResource
}

public class AdminReportsResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Lists moderation reports.
     *
     * Requires moderator or admin role (`[M]`).
     *
     * @param status Filter by report status (`pending`, `resolved`, or `dismissed`).
     * @param limit Items per page.
     * @param cursor Pagination cursor.
     * @return [Page] of [ReportSummary].
     */
    public suspend fun list(
        status: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): Page<ReportSummary> {
        val queryParams =
            mapOf(
                "status" to status,
                "limit" to limit,
                "cursor" to cursor,
            )
        val response = transport.get("/admin/reports", queryParams = queryParams)
        val parsed: ReportListResponse = response.parseJson()
        return Page(items = parsed.reports, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming moderation reports across pages.
     */
    public fun stream(
        status: String? = null,
        limit: Int? = null,
    ): Flow<ReportSummary> =
        paginateFlow { cursor ->
            list(status = status, limit = limit, cursor = cursor)
        }

    /**
     * Resolves or dismisses a report.
     *
     * Requires moderator or admin role (`[M]`).
     *
     * @param id The report ID.
     * @param status New status (`resolved` or `dismissed`).
     * @param notes Optional moderator notes.
     * @return [ReportSummary] with updated status.
     */
    public suspend fun update(
        id: String,
        status: String,
        notes: String? = null,
    ): ReportSummary {
        val request =
            UpdateReportRequest(
                status = status,
                notes = notes,
            )
        val response =
            transport.patch(
                path = "/admin/reports/$id",
                body = request.toJsonRequestBody(),
            )
        return response.parseJson()
    }
}

public class AdminContentsResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Moderates and permanently deletes content.
     *
     * Requires moderator or admin role (`[M]`).
     * A mandatory reason is recorded in the audit trail.
     *
     * @param id The content ID (`c_...`).
     * @param reason The required moderation reason.
     */
    public suspend fun delete(
        id: String,
        reason: String,
    ) {
        val request = ModerateDeleteRequest(reason = reason)
        transport.delete(
            path = "/admin/contents/$id",
            body = request.toJsonRequestBody(),
        )
    }
}

public class AdminBansResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Bans an actor.
     *
     * Requires moderator or admin role (`[M]`).
     *
     * @param username The username of the actor to ban.
     * @param reason The reason for the ban.
     * @param expiresAt Optional expiration timestamp (RFC 3339). If omitted, the ban is permanent.
     * @return [BanSummary] of the created ban.
     */
    public suspend fun create(
        username: String,
        reason: String,
        expiresAt: String? = null,
    ): BanSummary {
        val request =
            CreateBanRequest(
                username = username,
                reason = reason,
                expiresAt = expiresAt,
            )
        val response =
            transport.post(
                path = "/admin/bans",
                body = request.toJsonRequestBody(),
            )
        return response.parseJson()
    }

    /**
     * Unbans an actor.
     *
     * Requires moderator or admin role (`[M]`).
     * Idempotent: succeeds even if no ban exists.
     *
     * @param username The username of the actor to unban.
     */
    public suspend fun remove(username: String) {
        transport.delete("/admin/bans/$username")
    }
}

public class AdminRolesResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Assigns or clears a role on an actor.
     *
     * Requires admin role (`[X]`). Moderators cannot assign roles.
     *
     * @param username The username of the actor.
     * @param role Role string (`"admin"`, `"moderator"`, or `null` to revoke all roles).
     */
    public suspend fun set(
        username: String,
        role: String?,
    ) {
        val request =
            SetRoleRequest(
                username = username,
                role = role,
            )
        transport.post(
            path = "/admin/roles",
            body = request.toJsonRequestBody(),
        )
    }
}

public class AdminActionsResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Lists audit trail actions.
     *
     * Requires moderator or admin role (`[M]`).
     *
     * @param limit Items per page.
     * @param cursor Pagination cursor.
     * @return [Page] of [AdminActionSummary].
     */
    public suspend fun list(
        limit: Int? = null,
        cursor: String? = null,
    ): Page<AdminActionSummary> {
        val queryParams =
            mapOf(
                "limit" to limit,
                "cursor" to cursor,
            )
        val response = transport.get("/admin/actions", queryParams = queryParams)
        val parsed: AdminActionListResponse = response.parseJson()
        return Page(items = parsed.actions, nextCursor = parsed.nextCursor)
    }

    /**
     * Returns a cold [Flow] streaming audit trail actions across pages.
     */
    public fun stream(limit: Int? = null): Flow<AdminActionSummary> =
        paginateFlow { cursor ->
            list(limit = limit, cursor = cursor)
        }
}
