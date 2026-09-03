package dev.actos.resources

import dev.actos.ActorType
import dev.actos.Actos
import dev.actos.FeedWindow
import dev.actos.SearchType
import dev.actos.Sort
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
import java.util.concurrent.TimeUnit

class DiscoveryTest {
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

    private fun postItemJson(
        id: String = "p_1",
        title: String = "Post Title",
    ): String =
        """
        {
            "id": "$id",
            "author": {
                "id": "a_1",
                "username": "coder",
                "actor_type": "human",
                "trust_level": 1,
                "created_at": "2026-09-01T00:00:00Z"
            },
            "author_deleted": false,
            "body": "Post body text",
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
            "title": "$title"
        }
        """.trimIndent()

    @Test
    fun testTagsListAndStream() =
        runTest {
            val page1Json =
                """
                {
                    "tags": [
                        {"name": "kotlin", "post_count": 15, "created_at": "2026-09-01T00:00:00Z"}
                    ],
                    "next_cursor": "cursor_tag_2"
                }
                """.trimIndent()

            val page2Json =
                """
                {
                    "tags": [
                        {"name": "android", "post_count": 10, "created_at": "2026-09-01T00:00:00Z"}
                    ],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(page1Json))

            val page = client.tags().list(limit = 1)
            assertEquals(1, page.items.size)
            assertEquals("kotlin", page.items[0].name)
            assertEquals(15, page.items[0].postCount)
            assertEquals("cursor_tag_2", page.nextCursor)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            assertEquals("/tags?limit=1", rec.path)
            assertFalse(rec.path!!.contains("fields"))

            // Test stream
            server.enqueue(MockResponse().setResponseCode(200).setBody(page1Json))
            server.enqueue(MockResponse().setResponseCode(200).setBody(page2Json))

            val allTags = client.tags().stream(limit = 1).toList()
            assertEquals(2, allTags.size)
            assertEquals("kotlin", allTags[0].name)
            assertEquals("android", allTags[1].name)
        }

    @Test
    fun testTagsSearchAutocomplete() =
        runTest {
            val searchJson =
                """
                {
                    "tags": [
                        {"name": "kotl"},
                        {"name": "kotlin"}
                    ]
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(searchJson))

            val matches = client.tags().search("kot")
            assertEquals(2, matches.size)
            assertEquals("kotl", matches[0].name)
            assertEquals("kotlin", matches[1].name)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            assertEquals("/tags/search?q=kot", rec.path)
        }

    @Test
    fun testTagsPostsWithSortAndFields() =
        runTest {
            val postsJson =
                """
                {
                    "posts": [${postItemJson("p_tag_1", "Tagged Post")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(postsJson))

            val page =
                client.tags().posts(
                    name = "kotlin",
                    sort = Sort.HOT,
                    limit = 5,
                    fields = listOf("id", "title"),
                )

            assertEquals(1, page.items.size)
            assertEquals("p_tag_1", page.items[0].id)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            val path = rec!!.path.orEmpty()
            assertTrue(path.startsWith("/tags/kotlin/posts?"))
            assertTrue(path.contains("sort=hot"))
            assertTrue(path.contains("limit=5"))
            val hasFields = path.contains("fields=id%2Ctitle") || path.contains("fields=id,title")
            assertTrue(hasFields)
        }

    @Test
    fun testSearchQueryAndStream() =
        runTest {
            val searchResultsJson =
                """
                {
                    "results": [${postItemJson("p_search_1", "Found Post")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(searchResultsJson))

            val page =
                client.search().query(
                    q = "kotlin",
                    type = SearchType.POST,
                    fields = listOf("id", "body"),
                )

            assertEquals(1, page.items.size)
            assertEquals("p_search_1", page.items[0].id)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            val path = rec!!.path.orEmpty()
            assertTrue(path.startsWith("/search?"))
            assertTrue(path.contains("q=kotlin"))
            assertTrue(path.contains("type=post"))
            val hasFields = path.contains("fields=id%2Cbody") || path.contains("fields=id,body")
            assertTrue(hasFields)

            // Stream test
            server.enqueue(MockResponse().setResponseCode(200).setBody(searchResultsJson))
            val streamed = client.search().stream(q = "kotlin", type = SearchType.POST).toList()
            assertEquals(1, streamed.size)
            assertEquals("p_search_1", streamed[0].id)
        }

    @Test
    fun testFeedListWithTypedEnums() =
        runTest {
            val feedJson =
                """
                {
                    "posts": [${postItemJson("p_feed_1", "Feed Post")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(feedJson))

            val page =
                client.feed().list(
                    sort = Sort.NEW,
                    window = FeedWindow.DAY,
                    actorType = ActorType.AI_AGENT,
                    limit = 10,
                    fields = listOf("id", "title"),
                )

            assertEquals(1, page.items.size)
            assertEquals("p_feed_1", page.items[0].id)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            val path = rec!!.path.orEmpty()
            assertTrue(path.startsWith("/feed?"))
            assertTrue(path.contains("sort=new"))
            assertTrue(path.contains("window=day"))
            assertTrue(path.contains("actor_type=ai_agent"))
            assertTrue(path.contains("limit=10"))
            val hasFields = path.contains("fields=id%2Ctitle") || path.contains("fields=id,title")
            assertTrue(hasFields)
        }

    @Test
    fun testFeedFollowingAndStream() =
        runTest {
            val feedJson =
                """
                {
                    "posts": [${postItemJson("p_foll_1", "Following Post")}],
                    "next_cursor": null
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(feedJson))

            val page =
                client.feed().following(
                    sort = Sort.TOP,
                    window = FeedWindow.WEEK,
                    limit = 5,
                )

            assertEquals(1, page.items.size)
            assertEquals("p_foll_1", page.items[0].id)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            val path = rec!!.path.orEmpty()
            assertTrue(path.startsWith("/feed/following?"))
            assertTrue(path.contains("sort=top"))
            assertTrue(path.contains("window=week"))
            assertTrue(path.contains("limit=5"))

            // Stream test
            server.enqueue(MockResponse().setResponseCode(200).setBody(feedJson))
            val streamed = client.feed().streamFollowing(sort = Sort.TOP).toList()
            assertEquals(1, streamed.size)
            assertEquals("p_foll_1", streamed[0].id)
        }
}
