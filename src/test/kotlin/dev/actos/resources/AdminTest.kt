package dev.actos.resources

import dev.actos.Actos
import dev.actos.ForbiddenException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

class AdminTest {
    private lateinit var server: MockWebServer
    private lateinit var client: Actos

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            Actos(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                apiKey = "admin-secret-token",
            )
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    private fun reportJson(
        id: String = "rep_1",
        status: String = "pending",
    ): String =
        """
        {
            "id": "$id",
            "target_type": "post",
            "target_id": "p_bad",
            "reason": "Spam content",
            "status": "$status",
            "created_at": "2026-09-01T00:00:00Z",
            "notes": null,
            "resolved_at": null
        }
        """.trimIndent()

    private fun banJson(
        username: String = "spammer",
        reason: String = "Spam bot",
    ): String =
        """
        {
            "username": "$username",
            "reason": "$reason",
            "banned_at": "2026-09-01T00:00:00Z",
            "expires_at": null
        }
        """.trimIndent()

    private fun actionJson(
        id: String = "act_1",
        actionType: String = "ban_actor",
    ): String =
        """
        {
            "id": "$id",
            "action_type": "$actionType",
            "admin_username": "moderator_dan",
            "target_type": "actor",
            "target_id": 42,
            "reason": "Violation of rules",
            "created_at": "2026-09-01T00:00:00Z"
        }
        """.trimIndent()

    @Test
    fun testReportsCreate() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(reportJson()))

            val report =
                client.reports().create(
                    targetType = "post",
                    targetId = "p_bad",
                    reason = "Spam content",
                )
            assertEquals("rep_1", report.id)
            assertEquals("Spam content", report.reason)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("POST", rec!!.method)
            assertEquals("/reports", rec.path)
            val body = rec.body.readUtf8()
            assertTrue(body.contains("\"target_type\":\"post\""))
            assertTrue(body.contains("\"target_id\":\"p_bad\""))
            assertTrue(body.contains("\"reason\":\"Spam content\""))
        }

    @Test
    fun testAdminReportsListStreamAndUpdate() =
        runTest {
            val listJson =
                """
                {
                    "reports": [${reportJson("rep_1", "pending")}],
                    "next_cursor": null
                }
                """.trimIndent()

            // 1. List
            server.enqueue(MockResponse().setResponseCode(200).setBody(listJson))
            val page = client.admin().reports().list(status = "pending", limit = 10)
            assertEquals(1, page.items.size)
            assertEquals("rep_1", page.items[0].id)

            val recList = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recList)
            assertEquals("GET", recList!!.method)
            assertEquals("/admin/reports?status=pending&limit=10", recList.path)

            // 2. Stream
            server.enqueue(MockResponse().setResponseCode(200).setBody(listJson))
            val streamed = client.admin().reports().stream(status = "pending").toList()
            assertEquals(1, streamed.size)
            server.takeRequest(5, TimeUnit.SECONDS)

            // 3. Update report
            val resolvedReportJson = reportJson("rep_1", "resolved")
            server.enqueue(MockResponse().setResponseCode(200).setBody(resolvedReportJson))

            val updated = client.admin().reports().update("rep_1", status = "resolved", notes = "Post removed")
            assertEquals("resolved", updated.status)

            val recUpdate = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recUpdate)
            assertEquals("PATCH", recUpdate!!.method)
            assertEquals("/admin/reports/rep_1", recUpdate.path)
            val updateBody = recUpdate.body.readUtf8()
            assertTrue(updateBody.contains("\"status\":\"resolved\""))
            assertTrue(updateBody.contains("\"notes\":\"Post removed\""))
        }

    @Test
    fun testAdminContentsDelete() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            client.admin().contents().delete("c_bad_post", reason = "Hate speech violation")

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("DELETE", rec!!.method)
            assertEquals("/admin/contents/c_bad_post", rec.path)
            assertTrue(rec.body.readUtf8().contains("\"reason\":\"Hate speech violation\""))
        }

    @Test
    fun testAdminBansCreateAndRemove() =
        runTest {
            // 1. Create ban
            server.enqueue(MockResponse().setResponseCode(201).setBody(banJson("bad_actor", "Abusive behavior")))

            val ban = client.admin().bans().create("bad_actor", reason = "Abusive behavior")
            assertEquals("bad_actor", ban.username)
            assertEquals("Abusive behavior", ban.reason)

            val recCreate = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recCreate)
            assertEquals("POST", recCreate!!.method)
            assertEquals("/admin/bans", recCreate.path)
            assertTrue(recCreate.body.readUtf8().contains("\"username\":\"bad_actor\""))

            // 2. Remove ban (unban)
            server.enqueue(MockResponse().setResponseCode(204))
            client.admin().bans().remove("bad_actor")

            val recRemove = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recRemove)
            assertEquals("DELETE", recRemove!!.method)
            assertEquals("/admin/bans/bad_actor", recRemove.path)
        }

    @Test
    fun testAdminRolesSet() =
        runTest {
            // Assign moderator role
            server.enqueue(MockResponse().setResponseCode(204))

            client.admin().roles().set(username = "trusted_user", role = "moderator")

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("POST", rec!!.method)
            assertEquals("/admin/roles", rec.path)
            val body = rec.body.readUtf8()
            assertTrue(body.contains("\"username\":\"trusted_user\""))
            assertTrue(body.contains("\"role\":\"moderator\""))

            // Clear role (set null)
            server.enqueue(MockResponse().setResponseCode(204))
            client.admin().roles().set(username = "trusted_user", role = null)
            val recClear = server.takeRequest(5, TimeUnit.SECONDS)
            assertTrue(recClear!!.body.readUtf8().contains("\"role\":null"))
        }

    @Test
    fun testAdminActionsListAndStream() =
        runTest {
            val listJson =
                """
                {
                    "actions": [${actionJson("act_1", "ban_actor")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(listJson))

            val page = client.admin().actions().list(limit = 5)
            assertEquals(1, page.items.size)
            assertEquals("act_1", page.items[0].id)
            assertEquals("ban_actor", page.items[0].actionType)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            assertEquals("/admin/actions?limit=5", rec.path)

            // Stream
            server.enqueue(MockResponse().setResponseCode(200).setBody(listJson))
            val streamed = client.admin().actions().stream().toList()
            assertEquals(1, streamed.size)
            assertEquals("act_1", streamed[0].id)
        }

    @Test
    fun testAdminEndpointForbiddenThrowsForbiddenException() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"FORBIDDEN","status":403,"detail":"Moderator or admin privileges required"}"""),
            )

            val ex =
                assertThrows<ForbiddenException> {
                    client.admin().reports().list()
                }

            assertEquals(403, ex.status)
            assertTrue(ex.isForbidden)
            assertEquals("Moderator or admin privileges required", ex.detail)
        }
}
