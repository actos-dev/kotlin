package dev.actos.resources

import dev.actos.Actos
import dev.actos.model.NotificationSummary
import kotlinx.coroutines.flow.take
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
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class InboxTest {
    private lateinit var server: MockWebServer
    private lateinit var client: Actos

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            Actos(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                apiKey = "test-token",
            )
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    private fun notificationJson(
        id: String = "n_1",
        kind: String = "comment_on_post",
        targetId: String = "c_post_1",
        readAt: String? = null,
    ): String {
        val readAtField = if (readAt != null) "\"$readAt\"" else "null"
        return """
            {
                "id": "$id",
                "kind": "$kind",
                "target_type": "content",
                "target_id": "$targetId",
                "actor": {
                    "id": "a_sender",
                    "username": "sender_bob",
                    "actor_type": "human",
                    "trust_level": 1,
                    "created_at": "2026-09-01T00:00:00Z"
                },
                "payload": null,
                "created_at": "2026-09-01T00:00:00Z",
                "read_at": $readAtField
            }
            """.trimIndent()
    }

    @Test
    fun testInboxList() =
        runTest {
            val inboxJson =
                """
                {
                    "notifications": [${notificationJson("n_1")}],
                    "unread_count": 42,
                    "next_cursor": "cursor_inbox_2"
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(inboxJson))

            val page = client.inbox().list(unread = true, limit = 10)
            assertEquals(1, page.items.size)
            assertEquals("n_1", page.items[0].id)
            assertEquals(42, page.unreadCount)
            assertEquals("cursor_inbox_2", page.nextCursor)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            val path = rec.path.orEmpty()
            assertTrue(path.startsWith("/me/inbox?"))
            assertTrue(path.contains("unread=true"))
            assertTrue(path.contains("limit=10"))
        }

    @Test
    fun testInboxStream() =
        runTest {
            val page1Json =
                """
                {
                    "notifications": [${notificationJson("n_1")}],
                    "unread_count": 2,
                    "next_cursor": "c2"
                }
                """.trimIndent()

            val page2Json =
                """
                {
                    "notifications": [${notificationJson("n_2")}],
                    "unread_count": 2,
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(page1Json))
            server.enqueue(MockResponse().setResponseCode(200).setBody(page2Json))

            val items = client.inbox().stream().toList()
            assertEquals(2, items.size)
            assertEquals("n_1", items[0].id)
            assertEquals("n_2", items[1].id)
        }

    @Test
    fun testInboxReadSingleNotification() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(notificationJson("n_10", readAt = "2026-09-01T12:00:00Z")),
            )

            val updated = client.inbox().read("n_10")
            assertEquals("n_10", updated.id)
            assertEquals("2026-09-01T12:00:00Z", updated.readAt)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("POST", rec!!.method)
            assertEquals("/me/inbox/n_10/read", rec.path)
        }

    @Test
    fun testInboxReadAllIdempotent() =
        runTest {
            val markAllResponse = """{"marked": 5}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(markAllResponse))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"marked": 0}"""))

            // First call marks 5
            val res1 = client.inbox().readAll(upToCursor = "cur_1")
            assertEquals(5, res1.marked)

            val rec1 = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("POST", rec1!!.method)
            assertEquals("/me/inbox/read", rec1.path)
            assertTrue(rec1.body.readUtf8().contains("\"up_to_cursor\":\"cur_1\""))

            // Second call (idempotent) marks 0
            val res2 = client.inbox().readAll(upToCursor = "cur_1")
            assertEquals(0, res2.marked)
        }

    @Test
    fun testInboxUnreadCount() =
        runTest {
            val inboxJson =
                """
                {
                    "notifications": [],
                    "unread_count": 7,
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(inboxJson))

            val count = client.inbox().unreadCount()
            assertEquals(7, count)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("/me/inbox?limit=1", rec!!.path)
        }

    @Test
    fun testInboxWatchFlowPollsAndEmitsNewItems() =
        runTest {
            // Initial poll: has n_1 (seen baseline)
            val poll1 =
                """
                {
                    "notifications": [${notificationJson("n_1")}],
                    "unread_count": 1,
                    "next_cursor": null
                }
                """.trimIndent()

            // Second poll: has n_1 and new n_2 (emits n_2)
            val poll2 =
                """
                {
                    "notifications": [${notificationJson("n_2")}, ${notificationJson("n_1")}],
                    "unread_count": 2,
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(poll1))
            server.enqueue(MockResponse().setResponseCode(200).setBody(poll2))

            val emitted = mutableListOf<NotificationSummary>()
            client.inbox().watch(interval = 5.milliseconds).take(1).toList(emitted)

            assertEquals(1, emitted.size)
            assertEquals("n_2", emitted[0].id)
        }
}
