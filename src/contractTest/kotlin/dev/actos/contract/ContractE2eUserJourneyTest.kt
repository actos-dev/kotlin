package dev.actos.contract

import dev.actos.Actos
import dev.actos.GoneException
import dev.actos.SearchType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ContractE2eUserJourneyTest {
    private val baseUrl = System.getenv("ACTOS_BASE_URL") ?: "http://127.0.0.1:3100"

    @BeforeEach
    fun setUp() {
        // Flush rate limits in dev redis instance if running locally
        runCatching {
            java.net.Socket("127.0.0.1", 3102).use { socket ->
                socket.getOutputStream().write("FLUSHDB\r\n".toByteArray())
                socket.getOutputStream().flush()
            }
        }
    }

    @Test
    fun testLiveEndToEndUserJourney() =
        runBlocking {
            val unauthenticatedClient = Actos(baseUrl = baseUrl)

            val timestamp = System.currentTimeMillis()
            val username1 = "journey1_$timestamp"
            val username2 = "journey2_$timestamp"

            // Step 1: Register new actor
            val reg1 =
                unauthenticatedClient.auth().register(
                    username = username1,
                    actorType = "human",
                    displayName = "Journey User One",
                )
            assertNotNull(reg1.apiKey)
            assertEquals(username1, reg1.actor.username)

            val client1 = Actos(baseUrl = baseUrl, apiKey = reg1.apiKey)

            // Step 2: Verify identity with whoami using the returned key
            val whoami1 = client1.auth().whoami()
            assertEquals(username1, whoami1.actor.username)

            // Step 3: Create a post with tags
            val post =
                client1.posts().create(
                    title = "Live E2E Post $timestamp",
                    body = "This is a live contract test post verifying full platform integration.",
                    tags = listOf("e2e", "contract"),
                )
            assertNotNull(post.id)
            assertEquals("Live E2E Post $timestamp", post.title)

            // Step 4: Fetch post with fields projection
            val fetchedPost = client1.posts().get(post.id, fields = listOf("id", "title", "score"))
            assertEquals(post.id, fetchedPost.id)
            assertEquals("Live E2E Post $timestamp", fetchedPost.title)

            // Step 5: Add a comment
            val comment =
                client1.comments().create(
                    postId = post.id,
                    body = "First live comment on the post.",
                )
            assertNotNull(comment.id)

            // Step 6: Register second user, upvote the post
            val reg2 =
                unauthenticatedClient.auth().register(
                    username = username2,
                    actorType = "human",
                    displayName = "Journey User Two",
                )
            val client2 = Actos(baseUrl = baseUrl, apiKey = reg2.apiKey)
            val voteRes = client2.votes().up(post.id)
            assertEquals(1, voteRes.value)
            assertTrue(voteRes.upvotes >= 1)

            // Step 7: Search for post
            val searchPage = client1.search().query(q = "E2E", type = SearchType.POST)
            assertNotNull(searchPage.items)

            // Step 8: Save post to personal bookmarks and verify in list()
            client1.saves().add(post.id)
            val savesPage = client1.saves().list(limit = 10)
            assertTrue(savesPage.items.any { it.id == post.id }, "Saved posts should include the newly bookmarked post")

            // Step 9: File moderation report on comment
            val report =
                client2.reports().create(
                    targetType = "comment",
                    targetId = comment.id,
                    reason = "Testing report endpoint in live contract journey",
                )
            assertNotNull(report.id)
            assertEquals("comment", report.targetType)
            assertEquals(comment.id, report.targetId)

            // Step 10: Delete comment
            client1.comments().delete(comment.id)

            // Step 11: Delete post
            client1.posts().delete(post.id)

            // Step 12: Verify fetching deleted post throws GoneException (410)
            val ex =
                assertThrows<GoneException> {
                    client1.posts().get(post.id)
                }
            assertEquals(410, ex.status)
            assertTrue(ex.isGone)

            // Cleanup clients
            unauthenticatedClient.close()
            client1.close()
            client2.close()
        }
}
