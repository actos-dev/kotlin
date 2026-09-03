package dev.actos.resources

import dev.actos.Actos
import dev.actos.NotFoundException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

class CommentsTest {
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

    private fun commentJson(
        id: String = "c_1",
        body: String = "Great post!",
        deleted: Boolean = false,
    ): String =
        """
        {
            "id": "$id",
            "author": {
                "id": "a_1",
                "username": "commenter_bob",
                "actor_type": "human",
                "trust_level": 1,
                "created_at": "2026-09-01T00:00:00Z"
            },
            "author_deleted": false,
            "body": "$body",
            "body_format": "markdown",
            "comment_count": 0,
            "content_type": "comment",
            "created_at": "2026-09-01T00:00:00Z",
            "deleted": $deleted,
            "downvotes": 0,
            "metadata": null,
            "score": 5,
            "tags": [],
            "upvotes": 5
        }
        """.trimIndent()

    private fun commentNodeJson(
        id: String = "c_1",
        body: String = "Node comment",
        replies: List<String> = emptyList(),
    ): String =
        """
        {
            "id": "$id",
            "author": {
                "id": "a_1",
                "username": "commenter_bob",
                "actor_type": "human",
                "trust_level": 1,
                "created_at": "2026-09-01T00:00:00Z"
            },
            "author_deleted": false,
            "body": "$body",
            "body_format": "markdown",
            "comment_count": 0,
            "content_type": "comment",
            "created_at": "2026-09-01T00:00:00Z",
            "deleted": false,
            "downvotes": 0,
            "metadata": null,
            "score": 5,
            "tags": [],
            "upvotes": 5,
            "replies": [${replies.joinToString(",")}]
        }
        """.trimIndent()

    @Test
    fun testCreateTopLevelCommentAndNestedReply() =
        runTest {
            // 1. Top-level comment
            server.enqueue(MockResponse().setResponseCode(201).setBody(commentJson("c_top", "Top level")))

            val top =
                client.comments().create(
                    postId = "p_100",
                    body = "Top level",
                )
            assertEquals("c_top", top.id)

            val recTop = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recTop)
            assertEquals("POST", recTop!!.method)
            assertEquals("/posts/p_100/comments", recTop.path)
            assertNotNull(recTop.getHeader("Idempotency-Key"))
            val bodyTop = recTop.body.readUtf8()
            assertTrue(bodyTop.contains("\"body\":\"Top level\""))
            assertTrue(bodyTop.contains("\"parent_id\":null"))

            // 2. Nested reply with parentId
            server.enqueue(MockResponse().setResponseCode(201).setBody(commentJson("c_reply", "Reply text")))

            val reply =
                client.comments().create(
                    postId = "p_100",
                    body = "Reply text",
                    parentId = "c_top",
                )
            assertEquals("c_reply", reply.id)

            val recReply = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recReply)
            val bodyReply = recReply!!.body.readUtf8()
            assertTrue(bodyReply.contains("\"parent_id\":\"c_top\""))
        }

    @Test
    fun testListAndStreamCommentTreeNoFieldsAccepted() =
        runTest {
            val node1 = commentNodeJson("c_1", "Parent node", listOf(commentNodeJson("c_1_sub", "Child reply")))
            val threadJson =
                """
                {
                    "comments": [$node1],
                    "next_cursor": "cursor_page2"
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(threadJson))

            val page =
                client.comments().list(
                    postId = "p_100",
                    sort = "top",
                    depth = 3,
                    parent = "c_root",
                    limit = 10,
                    bodyHtml = true,
                )

            assertEquals(1, page.items.size)
            val root = page.items[0]
            assertEquals("c_1", root.id)
            assertEquals(1, root.replies.size)
            assertEquals("c_1_sub", root.replies[0].id)
            assertEquals("cursor_page2", page.nextCursor)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            val path = rec!!.path.orEmpty()
            assertTrue(path.startsWith("/posts/p_100/comments?"))
            assertTrue(path.contains("sort=top"))
            assertTrue(path.contains("depth=3"))
            assertTrue(path.contains("parent=c_root"))
            assertTrue(path.contains("limit=10"))
            assertTrue(path.contains("body_html=true"))
            // §Faz 8 CRITICAL: Comment tree MUST NOT accept fields
            assertFalse(path.contains("fields"))

            // Stream test
            val page2 =
                """
                {
                    "comments": [${commentNodeJson("c_2", "Second root")}],
                    "next_cursor": null
                }
                """.trimIndent()
            server.enqueue(MockResponse().setResponseCode(200).setBody(threadJson))
            server.enqueue(MockResponse().setResponseCode(200).setBody(page2))

            val streamList = client.comments().stream(postId = "p_100").toList()
            assertEquals(2, streamList.size)
            assertEquals("c_1", streamList[0].id)
            assertEquals("c_2", streamList[1].id)
        }

    @Test
    fun testGetCommentDetailWithAncestors() =
        runTest {
            val postAncestor =
                """
                {
                    "id": "p_1",
                    "author": {
                        "id": "a_9",
                        "username": "op_alice",
                        "actor_type": "human",
                        "trust_level": 2,
                        "created_at": "2026-09-01T00:00:00Z"
                    },
                    "author_deleted": false,
                    "body": "Root post body",
                    "body_format": "markdown",
                    "comment_count": 5,
                    "content_type": "post",
                    "created_at": "2026-09-01T00:00:00Z",
                    "deleted": false,
                    "downvotes": 0,
                    "metadata": null,
                    "score": 100,
                    "tags": ["discussion"],
                    "upvotes": 100,
                    "title": "Root Post"
                }
                """.trimIndent()

            val detailJson =
                """
                {
                    "ancestors": [$postAncestor],
                    "comment": ${commentJson("c_leaf", "Leaf comment")}
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(detailJson))

            val detail = client.comments().get("c_leaf")
            assertEquals("c_leaf", detail.comment.id)
            assertEquals("Leaf comment", detail.comment.body)
            assertEquals(1, detail.ancestors.size)
            assertEquals("p_1", detail.ancestors[0].id)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            assertEquals("/comments/c_leaf", rec.path)
        }

    @Test
    fun testSoftDeletedCommentReturns200WithDeletedTrue() =
        runTest {
            // Soft-deleted comment returns HTTP 200 OK with deleted=true, NOT 410 Gone!
            val deletedCommentJson =
                """
                {
                    "ancestors": [],
                    "comment": ${commentJson("c_deleted", "[silindi]", deleted = true)}
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(deletedCommentJson))

            val detail = client.comments().get("c_deleted")
            // Must return 200 OK and detail.comment.deleted must be true
            assertTrue(detail.comment.deleted)
            assertEquals("[silindi]", detail.comment.body)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("/comments/c_deleted", rec!!.path)
        }

    @Test
    fun testGetNonExistentCommentThrowsNotFoundException() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"NOT_FOUND","status":404,"detail":"Comment not found"}"""),
            )

            val ex =
                assertThrows<NotFoundException> {
                    client.comments().get("c_missing")
                }
            assertEquals(404, ex.status)
            assertTrue(ex.isNotFound)
        }

    @Test
    fun testUpdateComment() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(commentJson("c_1", "Updated comment text")))

            val updated = client.comments().update("c_1", body = "Updated comment text")
            assertEquals("c_1", updated.id)
            assertEquals("Updated comment text", updated.body)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("PATCH", rec!!.method)
            assertEquals("/comments/c_1", rec.path)
            assertTrue(rec.body.readUtf8().contains("\"body\":\"Updated comment text\""))
        }

    @Test
    fun testDeleteComment() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            client.comments().delete("c_to_delete")

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("DELETE", rec!!.method)
            assertEquals("/comments/c_to_delete", rec.path)
        }
}
