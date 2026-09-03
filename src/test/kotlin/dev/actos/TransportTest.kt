package dev.actos

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
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

class TransportTest {
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
    fun testDefaultHeaders() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val transport =
                Transport(
                    baseUrl = server.url("/"),
                    apiKey = "my-secret-token",
                )

            val response = transport.get("/health")
            assertEquals(200, response.code)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("actos-kotlin/${ActosVersion.VERSION}", recorded!!.getHeader("User-Agent"))
            assertEquals("application/json", recorded.getHeader("Accept"))
            assertEquals("Bearer my-secret-token", recorded.getHeader("Authorization"))
        }

    @Test
    fun testRateLimitTracking() =
        runTest {
            val mockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setBody("{}")
                    .setHeader("x-ratelimit-limit", "100")
                    .setHeader("x-ratelimit-remaining", "85")
                    .setHeader("x-ratelimit-reset", "1700000000")

            server.enqueue(mockResponse)

            val transport = Transport(baseUrl = server.url("/"))
            val response = transport.get("/test")
            assertEquals(200, response.code)

            val rateLimit = transport.rateLimit
            assertNotNull(rateLimit)
            assertEquals(100, rateLimit!!.limit)
            assertEquals(85, rateLimit.remaining)
            assertEquals(1700000000L, rateLimit.reset)
        }

    @Test
    fun testRetry5xxSuccess() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

            val transport = Transport(baseUrl = server.url("/"), maxRetries = 2)
            val response = transport.get("/retry-me")

            assertEquals(200, response.code)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun test4xxNeverRetried() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

            val transport = Transport(baseUrl = server.url("/"), maxRetries = 2)
            val exception =
                org.junit.jupiter.api.assertThrows<NotFoundException> {
                    transport.get("/not-found")
                }

            assertEquals(404, exception.status)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun testPostWithoutIdempotencyKeyNeverRetriedOn5xx() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

            val transport = Transport(baseUrl = server.url("/"), maxRetries = 2)
            val body = "{}".toRequestBody("application/json".toMediaType())
            val exception =
                org.junit.jupiter.api.assertThrows<InternalServerException> {
                    transport.post("/posts", body = body, idempotencyKey = null)
                }

            assertEquals(500, exception.status)
            // §2.6: POST without Idempotency-Key is NEVER retried on 5xx
            assertEquals(1, server.requestCount)
        }

    @Test
    fun testPostWithIdempotencyKeyRetriedOn5xx() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"p_1"}"""))

            val transport = Transport(baseUrl = server.url("/"), maxRetries = 2)
            val body = "{}".toRequestBody("application/json".toMediaType())
            val response = transport.post("/posts", body = body, idempotencyKey = "uuid-key-1234")

            assertEquals(200, response.code)
            // Retried because request has Idempotency-Key
            assertEquals(2, server.requestCount)
        }

    @Test
    fun test429RespectsRetryAfter() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "1")
                    .setBody("Rate limited"),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

            val transport = Transport(baseUrl = server.url("/"), maxRetries = 2)
            val response = transport.get("/limited")

            assertEquals(200, response.code)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun testAuthorizationHeaderRedacted() =
        runTest {
            val loggedMessages = mutableListOf<String>()
            val loggingInterceptor =
                HttpLoggingInterceptor { message ->
                    loggedMessages.add(message)
                }.apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                }

            val client = Transport.createDefaultOkHttpClient(loggingInterceptor)
            val transport =
                Transport(
                    baseUrl = server.url("/"),
                    apiKey = "secret-super-sensitive-key",
                    okHttpClient = client,
                )

            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val response = transport.get("/redacted")
            assertEquals(200, response.code)

            // §2.15: API key must never be logged; redactHeader("Authorization") must mask it
            val allLogs = loggedMessages.joinToString("\n")
            assertFalse(allLogs.contains("secret-super-sensitive-key"))
            assertTrue(allLogs.contains("Authorization: ██"))
        }

    @Test
    fun testBaseUrlNormalization() {
        val t1 = Transport("https://api.actos.dev")
        assertEquals("https://api.actos.dev/health", t1.urlFor("health").toString())
        assertEquals("https://api.actos.dev/health", t1.urlFor("/health").toString())

        val t2 = Transport("https://api.actos.dev/")
        assertEquals("https://api.actos.dev/health", t2.urlFor("health").toString())
        assertEquals("https://api.actos.dev/health", t2.urlFor("/health").toString())

        val t3 = Transport("https://api.actos.dev/v1")
        assertEquals("https://api.actos.dev/v1/posts", t3.urlFor("posts").toString())
        assertEquals("https://api.actos.dev/v1/posts", t3.urlFor("/posts").toString())

        val urlWithQuery =
            t1.urlFor(
                path = "/search",
                queryParams =
                    mapOf(
                        "q" to "kotlin",
                        "limit" to 10,
                        "empty" to null,
                    ),
            )
        assertEquals("https://api.actos.dev/search?q=kotlin&limit=10", urlWithQuery.toString())
    }

    @Test
    fun testApiKeyMaskingInToString() {
        val t1 = Transport("https://api.actos.dev", apiKey = "act_sec_1234567890abcdef")
        val str1 = t1.toString()
        assertTrue(str1.contains("act_…cdef"))
        assertFalse(str1.contains("1234567890"))

        val t2 = Transport("https://api.actos.dev", apiKey = null)
        assertTrue(t2.toString().contains("apiKey=null"))

        val t3 = Transport("https://api.actos.dev", apiKey = "short")
        assertTrue(t3.toString().contains("apiKey=…"))
    }
}
