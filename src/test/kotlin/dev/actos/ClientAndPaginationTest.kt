package dev.actos

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class ClientAndPaginationTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testDefaultConfiguration() {
        val client = Actos()
        assertEquals(Actos.DEFAULT_BASE_URL, client.baseUrl)
        assertEquals(Actos.DEFAULT_TIMEOUT, client.timeout)
        assertEquals(Actos.DEFAULT_MAX_RETRIES, client.maxRetries)
        assertNull(client.apiKey)
        assertNull(client.rateLimit)
        client.close()
    }

    @Test
    fun testBuilderConfiguration() {
        val client =
            Actos.builder()
                .apiKey("ak_live_1234567890abcdef")
                .baseUrl("https://api.actos.dev")
                .timeout(15.seconds)
                .maxRetries(3)
                .build()

        assertEquals("ak_live_1234567890abcdef", client.apiKey)
        assertEquals("https://api.actos.dev", client.baseUrl)
        assertEquals(15.seconds, client.timeout)
        assertEquals(3, client.maxRetries)
        client.close()
    }

    @Test
    fun testApiKeyMaskingInToString() {
        val client = Actos(apiKey = "ak_live_1234567890abcdef")
        val str = client.toString()
        assertTrue(str.contains("ak_l…cdef"))
        assertFalse(str.contains("1234567890"))
        client.close()

        val nullKeyClient = Actos(apiKey = null)
        assertTrue(nullKeyClient.toString().contains("apiKey=null"))
        nullKeyClient.close()
    }

    @Test
    fun testAllResourceAccessorsNonNull() {
        val client = Actos()
        assertNotNull(client.auth())
        assertNotNull(client.actors())
        assertNotNull(client.posts())
        assertNotNull(client.comments())
        assertNotNull(client.tags())
        assertNotNull(client.search())
        assertNotNull(client.feed())
        assertNotNull(client.votes())
        assertNotNull(client.saves())
        assertNotNull(client.uploads())
        assertNotNull(client.reports())
        assertNotNull(client.admin())
        assertNotNull(client.inbox())
        assertNotNull(client.meta())
        client.close()
    }

    @Test
    fun testRequestEscapeHatch() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"healthy"}"""))

            val client =
                Actos(
                    baseUrl = server.url("/").toString().removeSuffix("/"),
                    apiKey = "secret-token",
                )

            val response = client.request("GET", "/health")
            assertEquals(200, response.code)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("GET", recorded!!.method)
            assertEquals("/health", recorded.path)
            assertEquals("Bearer secret-token", recorded.getHeader("Authorization"))
            assertEquals("actos-kotlin/${ActosVersion.VERSION}", recorded.getHeader("User-Agent"))
            client.close()
        }

    @Test
    fun testPaginateFlowIteratesAllPages() =
        runTest {
            val pages =
                mapOf(
                    null to Page(items = listOf("item-1", "item-2"), nextCursor = "cursor-1"),
                    "cursor-1" to Page(items = listOf("item-3", "item-4"), nextCursor = "cursor-2"),
                    "cursor-2" to Page(items = listOf("item-5"), nextCursor = null),
                )

            val flow =
                paginateFlow { cursor ->
                    pages[cursor] ?: Page(items = emptyList(), nextCursor = null)
                }

            val collected = flow.toList()
            assertEquals(listOf("item-1", "item-2", "item-3", "item-4", "item-5"), collected)
        }

    @Test
    fun testPaginateFlowCancellationHaltsFetching() =
        runTest {
            val fetchCount = AtomicInteger(0)

            val flow =
                paginateFlow { cursor ->
                    val pageIndex = fetchCount.incrementAndGet()
                    Page(
                        items = listOf("page$pageIndex-item1", "page$pageIndex-item2"),
                        nextCursor = "cursor-$pageIndex",
                    )
                }

            // Taking 3 items should only require fetching page 1 and page 2 (total 4 items available, taking 3)
            val taken = flow.take(3).toList()
            assertEquals(listOf("page1-item1", "page1-item2", "page2-item1"), taken)
            // It should NOT fetch page 3 or further
            assertEquals(2, fetchCount.get())
        }
}
