package dev.actos.resources

import dev.actos.Actos
import dev.actos.GoneException
import dev.actos.NotFoundException
import dev.actos.Patch
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

class ActorsTest {
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

    private fun actorJson(
        id: String,
        username: String,
    ): String =
        """
        {
            "id": "$id",
            "username": "$username",
            "actor_type": "human",
            "trust_level": 1,
            "created_at": "2026-09-01T00:00:00Z"
        }
        """.trimIndent()

    private fun contentJson(
        id: String,
        type: String,
    ): String =
        """
        {
            "id": "$id",
            "author": ${actorJson("a_1", "alice")},
            "author_deleted": false,
            "body": "Test body",
            "body_format": "markdown",
            "comment_count": 0,
            "content_type": "$type",
            "created_at": "2026-09-01T00:00:00Z",
            "deleted": false,
            "downvotes": 0,
            "metadata": null,
            "score": 10,
            "tags": ["kotlin"],
            "upvotes": 10,
            "title": "Test Title"
        }
        """.trimIndent()

    @Test
    fun testListAndStreamActors() =
        runTest {
            val page1Json =
                """
                {
                    "actors": [${actorJson("a_1", "alice")}, ${actorJson("a_2", "bob")}],
                    "next_cursor": "cursor_2"
                }
                """.trimIndent()
            val page2Json =
                """
                {
                    "actors": [${actorJson("a_3", "charlie")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(page1Json))

            val page1 = client.actors().list(actorType = "human", limit = 2)
            assertEquals(2, page1.items.size)
            assertEquals("alice", page1.items[0].username)
            assertEquals("bob", page1.items[1].username)
            assertEquals("cursor_2", page1.nextCursor)
            assertTrue(page1.hasNext)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("GET", recorded!!.method)
            assertEquals("/actors?actor_type=human&limit=2", recorded.path)
            assertFalse(recorded.path!!.contains("fields"))

            // Stream both pages
            server.enqueue(MockResponse().setResponseCode(200).setBody(page1Json))
            server.enqueue(MockResponse().setResponseCode(200).setBody(page2Json))

            val all = client.actors().stream(actorType = "human").toList()
            assertEquals(3, all.size)
            assertEquals(listOf("alice", "bob", "charlie"), all.map { it.username })
        }

    @Test
    fun testGetProfileSuccessNotFoundAndGone() =
        runTest {
            val profileJson =
                """
                {
                    "actor": ${actorJson("a_1", "alice")},
                    "stats": {
                        "post_count": 10,
                        "comment_count": 25,
                        "total_score": 150
                    }
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(profileJson))

            val profile = client.actors().get("alice")
            assertEquals("a_1", profile.actor.id)
            assertEquals("alice", profile.actor.username)
            assertEquals(10L, profile.stats.postCount)
            assertEquals(25L, profile.stats.commentCount)
            assertEquals(150L, profile.stats.totalScore)

            // Test 404
            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"NOT_FOUND","status":404,"detail":"Actor not found"}"""),
            )
            assertThrows<NotFoundException> {
                client.actors().get("missing_user")
            }

            // Test 410
            server.enqueue(
                MockResponse()
                    .setResponseCode(410)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"GONE","status":410,"detail":"Actor account was deleted"}"""),
            )
            assertThrows<GoneException> {
                client.actors().get("deleted_user")
            }
        }

    @Test
    fun testUpdateMeTriStatePatch() =
        runTest {
            val updatedJson = actorJson("a_me", "my_user")

            // 1. Unchanged: omitted
            server.enqueue(MockResponse().setResponseCode(200).setBody(updatedJson))
            client.actors().updateMe(
                displayName = Patch.Value("New Name"),
                avatar = Patch.Unchanged,
            )
            val req1 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(req1)
            val body1 = req1!!.body.readUtf8()
            assertTrue(body1.contains("\"display_name\":\"New Name\""))
            assertFalse(body1.contains("\"avatar\""))

            // 2. Clear: explicit null
            server.enqueue(MockResponse().setResponseCode(200).setBody(updatedJson))
            client.actors().updateMe(
                avatar = Patch.Clear,
            )
            val req2 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(req2)
            val body2 = req2!!.body.readUtf8()
            assertTrue(body2.contains("\"avatar\":null"))

            // 3. Value: explicit value
            server.enqueue(MockResponse().setResponseCode(200).setBody(updatedJson))
            client.actors().updateMe(
                avatar = Patch.Value("up_new_avatar_123"),
            )
            val req3 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(req3)
            val body3 = req3!!.body.readUtf8()
            assertTrue(body3.contains("\"avatar\":\"up_new_avatar_123\""))
        }

    @Test
    fun testDeleteMe() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            client.actors().deleteMe(recoveryCode = "rec-code-12345")

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("DELETE", recorded!!.method)
            assertEquals("/actors/me", recorded.path)
            assertTrue(recorded.body.readUtf8().contains("\"recovery_code\":\"rec-code-12345\""))
        }

    @Test
    fun testFollowersAndFollowing() =
        runTest {
            val listJson =
                """
                {
                    "actors": [${actorJson("a_2", "bob")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(listJson))
            val followers = client.actors().followers("alice", limit = 10)
            assertEquals(1, followers.items.size)
            assertEquals("bob", followers.items[0].username)

            val recFollowers = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("GET", recFollowers!!.method)
            assertEquals("/actors/alice/followers?limit=10", recFollowers.path)

            server.enqueue(MockResponse().setResponseCode(200).setBody(listJson))
            val following = client.actors().following("alice", limit = 5)
            assertEquals(1, following.items.size)
            assertEquals("bob", following.items[0].username)

            val recFollowing = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("GET", recFollowing!!.method)
            assertEquals("/actors/alice/following?limit=5", recFollowing.path)
        }

    @Test
    fun testPostsAndCommentsWithFields() =
        runTest {
            val postsJson =
                """
                {
                    "posts": [${contentJson("p_1", "post")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(postsJson))

            val posts =
                client.actors().posts(
                    username = "alice",
                    sort = "new",
                    fields = listOf("id", "title", "score"),
                )
            assertEquals(1, posts.items.size)
            assertEquals("p_1", posts.items[0].id)

            val recPosts = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recPosts)
            assertEquals("GET", recPosts!!.method)
            assertTrue(recPosts.path!!.startsWith("/actors/alice/posts?"))
            assertTrue(recPosts.path!!.contains("sort=new"))
            val path = recPosts.path.orEmpty()
            val hasFields = path.contains("fields=id%2Ctitle%2Cscore") || path.contains("fields=id,title,score")
            assertTrue(hasFields)

            val commentsJson =
                """
                {
                    "comments": [${contentJson("c_1", "comment")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(commentsJson))

            val comments =
                client.actors().comments(
                    username = "alice",
                    fields = listOf("id", "body"),
                )
            assertEquals(1, comments.items.size)
            assertEquals("c_1", comments.items[0].id)

            val recComments = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recComments)
            assertEquals("GET", recComments!!.method)
            assertTrue(recComments.path!!.startsWith("/actors/alice/comments?"))
            val commentPath = recComments.path.orEmpty()
            val hasCommentFields = commentPath.contains("fields=id%2Cbody") || commentPath.contains("fields=id,body")
            assertTrue(hasCommentFields)
        }

    @Test
    fun testFollowAndUnfollowIdempotency() =
        runTest {
            // Calling follow twice
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))

            client.actors().follow("charlie")
            client.actors().follow("charlie")

            val rec1 = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("PUT", rec1!!.method)
            assertEquals("/actors/charlie/follow", rec1.path)

            val rec2 = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("PUT", rec2!!.method)
            assertEquals("/actors/charlie/follow", rec2.path)

            // Calling unfollow twice
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))

            client.actors().unfollow("charlie")
            client.actors().unfollow("charlie")

            val rec3 = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("DELETE", rec3!!.method)
            assertEquals("/actors/charlie/follow", rec3.path)

            val rec4 = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("DELETE", rec4!!.method)
            assertEquals("/actors/charlie/follow", rec4.path)
        }
}
