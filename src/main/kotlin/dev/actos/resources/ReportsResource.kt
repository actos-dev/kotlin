package dev.actos.resources

import dev.actos.Transport
import dev.actos.model.CreateReportRequest
import dev.actos.model.ReportSummary

public class ReportsResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Files a report against a post or comment for moderation review.
     *
     * Requires authentication (`[A]`).
     *
     * @param targetType The target content type (`"post"` or `"comment"`).
     * @param targetId The ID of the post or comment.
     * @param reason Description of why the content is being reported.
     * @return [ReportSummary] of the filed report.
     */
    public suspend fun create(
        targetType: String,
        targetId: String,
        reason: String,
    ): ReportSummary {
        val request =
            CreateReportRequest(
                targetType = targetType,
                targetId = targetId,
                reason = reason,
            )
        val response =
            transport.post(
                path = "/reports",
                body = request.toJsonRequestBody(),
            )
        return response.parseJson()
    }
}
