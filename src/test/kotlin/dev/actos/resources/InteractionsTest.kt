package dev.actos.resources

import dev.actos.Actos
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

class InteractionsTest {
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

    private fun voteResponseJson(
        upvotes: Int = 10,
        downvotes: Int = 0,
        score: Int = 10,
        value: Int = 1,
    ): String =
        """
        {
            "upvotes": $upvotes,
            "downvotes": $downvotes,
            "score": $score,
            "value": $value
        }
        """.trimIndent()

    private fun savedContentJson(id: String): String =
        """
        {
            "id": "$id",
            "author": {
                "id": "a_1",
                "username": "author_alice",
                "actor_type": "human",
                "trust_level": 1,
                "created_at": "2026-09-01T00:00:00Z"
            },
            "author_deleted": false,
            "body": "Saved content body",
            "body_format": "markdown",
            "comment_count": 0,
            "content_type": "post",
            "created_at": "2026-09-01T00:00:00Z",
            "deleted": false,
            "downvotes": 0,
            "metadata": null,
            "score": 10,
            "tags": ["kotlin"],
            "upvotes": 10,
            "title": "Saved Title"
        }
        """.trimIndent()

    @Test
    fun testVotesSetUpDownClear() =
        runTest {
            // 1. Set explicit value
            server.enqueue(MockResponse().setResponseCode(200).setBody(voteResponseJson(value = 1)))
            val resSet = client.votes().set("c_1", 1)
            assertEquals(1, resSet.value)
            val recSet = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recSet)
            assertEquals("PUT", recSet!!.method)
            assertEquals("/contents/c_1/vote", recSet.path)
            assertTrue(recSet.body.readUtf8().contains("\"value\":1"))

            // 2. Upvote (+1)
            server.enqueue(MockResponse().setResponseCode(200).setBody(voteResponseJson(value = 1)))
            val resUp = client.votes().up("c_1")
            assertEquals(1, resUp.value)
            val recUp = server.takeRequest(5, TimeUnit.SECONDS)
            assertTrue(recUp!!.body.readUtf8().contains("\"value\":1"))

            // 3. Downvote (-1)
            server.enqueue(MockResponse().setResponseCode(200).setBody(voteResponseJson(value = -1, score = 8)))
            val resDown = client.votes().down("c_1")
            assertEquals(-1, resDown.value)
            val recDown = server.takeRequest(5, TimeUnit.SECONDS)
            assertTrue(recDown!!.body.readUtf8().contains("\"value\":-1"))

            // 4. Clear vote (0)
            server.enqueue(MockResponse().setResponseCode(200).setBody(voteResponseJson(value = 0, score = 9)))
            val resClear = client.votes().clear("c_1")
            assertEquals(0, resClear.value)
            val recClear = server.takeRequest(5, TimeUnit.SECONDS)
            assertTrue(recClear!!.body.readUtf8().contains("\"value\":0"))
        }

    @Test
    fun testVotesList() =
        runTest {
            val voteMapJson =
                """
                {
                    "votes": {
                        "c_1": 1,
                        "c_2": -1
                    }
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(voteMapJson))

            val votes = client.votes().list(contentIds = listOf("c_1", "c_2", "c_3"))
            assertEquals(2, votes.size)
            assertEquals(1, votes["c_1"])
            assertEquals(-1, votes["c_2"])

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            val path = rec.path.orEmpty()
            assertTrue(path.startsWith("/me/votes?"))
            val hasContentIds = path.contains("content_ids=c_1%2Cc_2%2Cc_3") || path.contains("content_ids=c_1,c_2,c_3")
            assertTrue(hasContentIds)

            // Test without contentIds
            server.enqueue(MockResponse().setResponseCode(200).setBody(voteMapJson))
            client.votes().list()
            val rec2 = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("/me/votes", rec2!!.path)
        }

    @Test
    fun testSavesAddAndRemove() =
        runTest {
            // Add save
            server.enqueue(MockResponse().setResponseCode(204))
            client.saves().add("c_100")
            val recAdd = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recAdd)
            assertEquals("PUT", recAdd!!.method)
            assertEquals("/contents/c_100/save", recAdd.path)

            // Remove save
            server.enqueue(MockResponse().setResponseCode(204))
            client.saves().remove("c_100")
            val recRemove = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recRemove)
            assertEquals("DELETE", recRemove!!.method)
            assertEquals("/contents/c_100/save", recRemove.path)
        }

    @Test
    fun testSavesListAndStream() =
        runTest {
            val page1Json =
                """
                {
                    "saves": [${savedContentJson("c_1")}],
                    "next_cursor": "cursor_save_2"
                }
                """.trimIndent()
            val page2Json =
                """
                {
                    "saves": [${savedContentJson("c_2")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(page1Json))

            val page1 = client.saves().list(limit = 1, fields = listOf("id", "title"))
            assertEquals(1, page1.items.size)
            assertEquals("c_1", page1.items[0].id)
            assertEquals("cursor_save_2", page1.nextCursor)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            val path = rec!!.path.orEmpty()
            assertTrue(path.startsWith("/me/saves?"))
            assertTrue(path.contains("limit=1"))
            val hasFields = path.contains("fields=id%2Ctitle") || path.contains("fields=id,title")
            assertTrue(hasFields)

            // Stream test
            server.enqueue(MockResponse().setResponseCode(200).setBody(page1Json))
            server.enqueue(MockResponse().setResponseCode(200).setBody(page2Json))

            val allSaved = client.saves().stream().toList()
            assertEquals(2, allSaved.size)
            assertEquals("c_1", allSaved[0].id)
            assertEquals("c_2", allSaved[1].id)
        }

    @Test
    fun testIdempotentRepeatedInteractions() =
        runTest {
            // 1. Voting twice with same value
            server.enqueue(MockResponse().setResponseCode(200).setBody(voteResponseJson(value = 1)))
            server.enqueue(MockResponse().setResponseCode(200).setBody(voteResponseJson(value = 1)))

            val v1 = client.votes().up("c_target")
            val v2 = client.votes().up("c_target")
            assertEquals(1, v1.value)
            assertEquals(1, v2.value)

            // 2. Saving twice
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))

            client.saves().add("c_target")
            client.saves().add("c_target")

            // 3. Removing save twice
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))

            client.saves().remove("c_target")
            client.saves().remove("c_target")
        }
}
