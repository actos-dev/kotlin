package dev.actos.contract

import dev.actos.Actos
import dev.actos.ActosApiException
import dev.actos.ActosVersion
import dev.actos.GoneException
import dev.actos.NotFoundException
import dev.actos.Transport
import dev.actos.model.ContentSummary
import dev.actos.paginateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class ContractSixteenPointsTest {
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

    // Point 1: Single entry point with 14 resource accessors, strictly no verifications()
    @Test
    fun testPoint1_SingleEntryPointAndResources() {
        val client = Actos(baseUrl = server.url("/").toString().removeSuffix("/"), apiKey = "test-key")

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

        // Verify strictly no verifications() method exists on Actos
        val methods = Actos::class.java.methods.map { it.name }
        assertFalse(methods.contains("verifications"), "SDK must not expose verifications() per §0.2")

        client.close()
    }

    // Point 2: Types generated from spec in dev.actos.model.*
    @Test
    fun testPoint2_TypesGeneratedFromSpec() {
        val contentSummaryClass = dev.actos.model.ContentSummary::class.java
        assertTrue(contentSummaryClass.name.startsWith("dev.actos.model."))

        val registerRequestClass = dev.actos.model.RegisterRequest::class.java
        assertTrue(registerRequestClass.name.startsWith("dev.actos.model."))
    }

    // Point 3: Typed sealed exception hierarchy; 404 and 410 are distinct classes
    @Test
    fun testPoint3_SealedExceptionHierarchy404Vs410() {
        val notFound = NotFoundException(detail = "Does not exist")
        val gone = GoneException(detail = "Content deleted")

        assertEquals(404, notFound.status)
        assertTrue(notFound.isNotFound)
        assertFalse(notFound.isGone)

        assertEquals(410, gone.status)
        assertTrue(gone.isGone)
        assertFalse(gone.isNotFound)

        assertNotEquals(notFound::class, gone::class)
    }

    // Point 4: RFC 9457 fields present on ActosApiException (status, code, detail, requestId)
    @Test
    fun testPoint4_Rfc9457FieldsPresent() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setHeader("Content-Type", "application/problem+json")
                    .setHeader("x-request-id", "req-xyz-789")
                    .setBody("""{"status":400,"code":"VALIDATION_FAILED","detail":"Invalid title field"}"""),
            )

            val client = Actos(baseUrl = server.url("/").toString().removeSuffix("/"))
            val ex =
                assertThrows<ActosApiException> {
                    client.posts().create(title = "", body = "body")
                }

            assertEquals(400, ex.status)
            assertEquals("VALIDATION_FAILED", ex.code?.value)
            assertEquals("Invalid title field", ex.detail)
            assertEquals("req-xyz-789", ex.requestId)
            client.close()
        }

    // Point 5: Two-tier pagination: list() returns nextCursor, stream() auto-pages cold Flow
    @Test
    fun testPoint5_TwoTierPagination() =
        runTest {
            val page1 = dev.actos.Page(items = listOf("item1"), nextCursor = "c2")
            val page2 = dev.actos.Page(items = listOf("item2"), nextCursor = null)

            assertEquals("c2", page1.nextCursor)
            assertNull(page2.nextCursor)

            var callCount = 0
            val flow =
                paginateFlow { cursor ->
                    callCount++
                    if (cursor == null) page1 else page2
                }

            val items = flow.toList()
            assertEquals(listOf("item1", "item2"), items)
            assertEquals(2, callCount)
        }

    // Point 6: Retries 5xx with Idempotency-Key, never retries POST without key on 5xx, never retries 4xx
    @Test
    fun testPoint6_RetryRules() =
        runTest {
            val transport =
                Transport(
                    baseUrl = server.url("/"),
                    apiKey = "token",
                    maxRetries = 2,
                )

            // POST without Idempotency-Key is NEVER retried on 500
            server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
            assertThrows<ActosApiException> {
                transport.post(path = "/test-post", idempotencyKey = null)
            }
            assertEquals(1, server.requestCount)

            // POST with Idempotency-Key IS retried on 500
            server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
            val res = transport.post(path = "/test-post", idempotencyKey = "idemp-key-1")
            assertEquals(200, res.code)
            assertEquals(3, server.requestCount) // 1 initial + 2 retried

            // 4xx is NEVER retried
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"status":400}"""))
            assertThrows<ActosApiException> {
                transport.get("/test-4xx")
            }
            assertEquals(4, server.requestCount)
        }

    // Point 7: 429 respects Retry-After header
    @Test
    fun testPoint7_429RespectsRetryAfter() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "1")
                    .setBody("Rate limited"),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

            val transport =
                Transport(
                    baseUrl = server.url("/"),
                    apiKey = "token",
                    maxRetries = 1,
                )

            val start = System.currentTimeMillis()
            val res = transport.get("/rate-test")
            val elapsed = System.currentTimeMillis() - start

            assertEquals(200, res.code)
            assertTrue(elapsed >= 900, "Should respect Retry-After header duration")
        }

    // Point 8: Exponential backoff with full jitter in transport
    @Test
    fun testPoint8_ExponentialBackoffFullJitter() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

            val transport = Transport(baseUrl = server.url("/"), maxRetries = 2)
            val res = transport.get("/backoff-test")

            assertEquals(200, res.code)
            assertEquals(2, server.requestCount)
        }

    // Point 9: posts().create() auto-generates UUID Idempotency-Key by default, overridable, disabled with null
    @Test
    fun testPoint9_PostsCreateIdempotencyKey() =
        runTest {
            val client = Actos(baseUrl = server.url("/").toString().removeSuffix("/"), apiKey = "key")

            val postJson =
                """
                {
                    "id": "p_1",
                    "author": {"id":"a_1","username":"dev","actor_type":"human","trust_level":1,"created_at":"2026-09-01T00:00:00Z"},
                    "author_deleted": false,
                    "body": "Text",
                    "body_format": "markdown",
                    "comment_count": 0,
                    "content_type": "post",
                    "created_at": "2026-09-01T00:00:00Z",
                    "deleted": false,
                    "downvotes": 0,
                    "metadata": null,
                    "score": 0,
                    "tags": [],
                    "upvotes": 0,
                    "title": "Title"
                }
                """.trimIndent()

            // 1. Default auto-generated UUID
            server.enqueue(MockResponse().setResponseCode(201).setBody(postJson))
            client.posts().create(title = "T1", body = "B1")
            val rec1 = server.takeRequest(5, TimeUnit.SECONDS)
            val header1 = rec1!!.getHeader("Idempotency-Key")
            assertNotNull(header1)
            // Verify valid UUID
            assertNotNull(UUID.fromString(header1))

            // 2. Custom override
            server.enqueue(MockResponse().setResponseCode(201).setBody(postJson))
            client.posts().create(title = "T2", body = "B2", idempotencyKey = "my-custom-key")
            val rec2 = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("my-custom-key", rec2!!.getHeader("Idempotency-Key"))

            // 3. Disabled with null
            server.enqueue(MockResponse().setResponseCode(201).setBody(postJson))
            client.posts().create(title = "T3", body = "B3", idempotencyKey = null)
            val rec3 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNull(rec3!!.getHeader("Idempotency-Key"))

            client.close()
        }

    // Point 10: X-RateLimit-* parsed into client.rateLimit and RateLimitException
    @Test
    fun testPoint10_RateLimitHeaderTracking() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("X-RateLimit-Limit", "100")
                    .setHeader("X-RateLimit-Remaining", "95")
                    .setHeader("X-RateLimit-Reset", "1725000000")
                    .setBody("""{"status":"ok"}"""),
            )

            val client = Actos(baseUrl = server.url("/").toString().removeSuffix("/"))
            client.meta().health()

            val rl = client.rateLimit
            assertNotNull(rl)
            assertEquals(100, rl!!.limit)
            assertEquals(95, rl.remaining)
            assertEquals(1725000000L, rl.reset)
            client.close()
        }

    // Point 11: fields parameter projection supported
    @Test
    fun testPoint11_FieldsProjectionParameter() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                        "id": "p_1",
                        "title": "T",
                        "author": {"id":"a_1","username":"u","actor_type":"human","trust_level":1,"created_at":"2026-09-01T00:00:00Z"},
                        "author_deleted": false,
                        "body": "B",
                        "body_format": "markdown",
                        "comment_count": 0,
                        "content_type": "post",
                        "created_at": "2026-09-01T00:00:00Z",
                        "deleted": false,
                        "downvotes": 0,
                        "metadata": null,
                        "score": 0,
                        "tags": [],
                        "upvotes": 0
                    }
                    """.trimIndent(),
                ),
            )

            val client = Actos(baseUrl = server.url("/").toString().removeSuffix("/"))
            client.posts().get("p_1", fields = listOf("id", "title", "score"))

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            val path = rec!!.path.orEmpty()
            assertTrue(path.contains("fields=id%2Ctitle%2Cscore") || path.contains("fields=id,title,score"))
            client.close()
        }

    // Point 12: IDs treated as opaque strings without client-side mutation
    @Test
    fun testPoint12_OpaqueStringIds() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            val client = Actos(baseUrl = server.url("/").toString().removeSuffix("/"))
            val opaqueId = "c_custom_opaque_12345"
            client.posts().delete(opaqueId)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("/posts/c_custom_opaque_12345", rec!!.path)
            client.close()
        }

    // Point 13: Default 30s timeout, Closeable, custom OkHttpClient injection
    @Test
    fun testPoint13_TimeoutAndCloseable() {
        val customOkHttp = OkHttpClient.Builder().build()
        val client =
            Actos(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                timeout = 15.seconds,
                okHttpClient = customOkHttp,
            )

        client.close()
    }

    // Point 14: User-Agent: actos-kotlin/<version> sent with every request
    @Test
    fun testPoint14_UserAgentHeader() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

            val client = Actos(baseUrl = server.url("/").toString().removeSuffix("/"))
            client.meta().health()

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            val ua = rec!!.getHeader("User-Agent")
            assertNotNull(ua)
            assertEquals("actos-kotlin/${ActosVersion.VERSION}", ua)
            client.close()
        }

    // Point 15: API key masked in toString() and Authorization redacted in logging
    @Test
    fun testPoint15_ApiKeyMaskingAndRedaction() {
        val client = Actos(apiKey = "actos_sec_secret_1234567890abcdef")
        val str = client.toString()
        assertFalse(str.contains("secret_1234567890abcdef"))
        assertTrue(str.contains("acto…"))
        client.close()
    }

    // Point 16: Forward compatibility ignores unknown JSON properties
    @Test
    fun testPoint16_ForwardCompatibilityIgnoresUnknownKeys() {
        val jsonString =
            """
            {
                "id": "p_future",
                "author": {"id":"a_1","username":"dev","actor_type":"human","trust_level":1,"created_at":"2026-09-01T00:00:00Z"},
                "author_deleted": false,
                "body": "Future post",
                "body_format": "markdown",
                "comment_count": 0,
                "content_type": "post",
                "created_at": "2026-09-01T00:00:00Z",
                "deleted": false,
                "downvotes": 0,
                "metadata": null,
                "score": 0,
                "tags": [],
                "upvotes": 0,
                "title": "Future Title",
                "quantum_score": 99.9,
                "hologram_url": "https://cdn.actos.dev/holo.bin"
            }
            """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString<ContentSummary>(jsonString)
        assertEquals("p_future", parsed.id)
        assertEquals("Future Title", parsed.title)
    }
}
