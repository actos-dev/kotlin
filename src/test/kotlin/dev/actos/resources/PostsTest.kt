package dev.actos.resources

import dev.actos.Actos
import dev.actos.GoneException
import dev.actos.InternalServerException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import java.util.concurrent.TimeUnit

class PostsTest {
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

    private fun postJson(
        id: String = "p_1",
        title: String = "Test Post",
        body: String = "Post content",
    ): String =
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
            "body": "$body",
            "body_format": "markdown",
            "comment_count": 0,
            "content_type": "post",
            "created_at": "2026-09-01T00:00:00Z",
            "deleted": false,
            "downvotes": 0,
            "metadata": null,
            "score": 0,
            "tags": ["kotlin"],
            "upvotes": 0,
            "title": "$title"
        }
        """.trimIndent()

    @Test
    fun testCreateAutoGeneratesUuidIdempotencyKeyByDefault() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(postJson(title = "Hello Actos")))

            val post =
                client.posts().create(
                    title = "Hello Actos",
                    body = "Kotlin SDK is great!",
                    tags = listOf("kotlin", "sdk"),
                )
            assertEquals("p_1", post.id)
            assertEquals("Hello Actos", post.title)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("POST", recorded!!.method)
            assertEquals("/posts", recorded.path)

            val idempotencyHeader = recorded.getHeader("Idempotency-Key")
            assertNotNull(idempotencyHeader)
            // Verify it's a valid UUID
            assertNotNull(UUID.fromString(idempotencyHeader!!))

            val body = recorded.body.readUtf8()
            assertTrue(body.contains("\"title\":\"Hello Actos\""))
            assertTrue(body.contains("\"tags\":[\"kotlin\",\"sdk\"]"))
        }

    @Test
    fun testCreateWithCustomIdempotencyKey() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(postJson()))

            client.posts().create(
                title = "Title",
                body = "Body",
                idempotencyKey = "custom-key-xyz-789",
            )

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("custom-key-xyz-789", recorded!!.getHeader("Idempotency-Key"))
        }

    @Test
    fun testCreateWithNullIdempotencyKeySendsNoHeader() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(postJson()))

            client.posts().create(
                title = "Title",
                body = "Body",
                idempotencyKey = null,
            )

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertNull(recorded!!.getHeader("Idempotency-Key"))
        }

    @Test
    fun testCreateWithMetadataAndAttachments() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(postJson()))

            val metadata =
                buildJsonObject {
                    put("client", "kotlin-sdk")
                    put("build", 42)
                }

            client.posts().create(
                title = "With Attachments",
                body = "Body text",
                attachmentIds = listOf("up_att_1", "up_att_2"),
                metadata = metadata,
            )

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            val body = recorded!!.body.readUtf8()
            assertTrue(body.contains("\"attachment_ids\":[\"up_att_1\",\"up_att_2\"]"))
            assertTrue(body.contains("\"metadata\":{\"client\":\"kotlin-sdk\",\"build\":42}"))
        }

    @Test
    fun testPostWithIdempotencyKeyRetriesOn500AndSucceeds() =
        runTest {
            // Transient 500 error followed by 201 success
            server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
            server.enqueue(MockResponse().setResponseCode(201).setBody(postJson(id = "p_retry_ok")))

            val post =
                client.posts().create(
                    title = "Retry Me",
                    body = "Will retry on 500",
                )

            assertEquals("p_retry_ok", post.id)
            // §2.6: Since an Idempotency-Key was automatically attached, it retried and made 2 requests
            assertEquals(2, server.requestCount)
        }

    @Test
    fun testPostWithoutIdempotencyKeyNeverRetriedOn500() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

            val ex =
                assertThrows<InternalServerException> {
                    client.posts().create(
                        title = "No Retry",
                        body = "Will not retry without idempotency key",
                        idempotencyKey = null,
                    )
                }

            assertEquals(500, ex.status)
            // §2.6: POST without Idempotency-Key is NEVER retried on 5xx
            assertEquals(1, server.requestCount)
        }

    @Test
    fun testGetWithAndWithoutFields() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(postJson(id = "p_100")))

            val post1 = client.posts().get("p_100")
            assertEquals("p_100", post1.id)

            val rec1 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec1)
            assertEquals("GET", rec1!!.method)
            assertEquals("/posts/p_100", rec1.path)

            // With fields
            server.enqueue(MockResponse().setResponseCode(200).setBody(postJson(id = "p_100")))
            client.posts().get("p_100", fields = listOf("id", "title", "body_html"))

            val rec2 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec2)
            val path2 = rec2!!.path.orEmpty()
            assertTrue(
                path2.contains("fields=id%2Ctitle%2Cbody_html") || path2.contains("fields=id,title,body_html"),
            )
        }

    @Test
    fun testUpdatePost() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(postJson(id = "p_2", title = "Updated Title")))

            val updated = client.posts().update("p_2", title = "Updated Title", body = "Updated body")
            assertEquals("Updated Title", updated.title)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("PATCH", recorded!!.method)
            assertEquals("/posts/p_2", recorded.path)
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("\"title\":\"Updated Title\""))
            assertTrue(body.contains("\"body\":\"Updated body\""))
        }

    @Test
    fun testDeleteFollowedByGetThrowsGoneException() =
        runTest {
            // Delete post
            server.enqueue(MockResponse().setResponseCode(204))

            client.posts().delete("p_deleted_post")

            val recDelete = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recDelete)
            assertEquals("DELETE", recDelete!!.method)
            assertEquals("/posts/p_deleted_post", recDelete.path)

            // Subsequent GET on soft-deleted post returns 410 Gone
            server.enqueue(
                MockResponse()
                    .setResponseCode(410)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"GONE","status":410,"detail":"Post has been deleted"}"""),
            )

            val goneEx =
                assertThrows<GoneException> {
                    client.posts().get("p_deleted_post")
                }
            assertEquals(410, goneEx.status)
            assertTrue(goneEx.isGone)
            assertEquals("Post has been deleted", goneEx.detail)
        }
}
