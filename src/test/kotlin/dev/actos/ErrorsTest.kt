package dev.actos

import dev.actos.model.ErrorCode
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

class ErrorsTest {
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

    private fun mockResponse(
        code: Int,
        headers: Headers = Headers.headersOf(),
    ): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.actos.dev/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .headers(headers)
            .body("{}".toResponseBody())
            .build()

    @Test
    fun testAll12ErrorCodesMapToCorrectSubclass() {
        val testCases =
            listOf(
                Triple(ErrorCode.VALIDATION_FAILED, 400, ValidationException::class),
                Triple(ErrorCode.INVALID_CURSOR, 400, InvalidCursorException::class),
                Triple(ErrorCode.MISSING_CREDENTIALS, 401, AuthenticationException::class),
                Triple(ErrorCode.INVALID_KEY, 401, InvalidKeyException::class),
                Triple(ErrorCode.FORBIDDEN, 403, ForbiddenException::class),
                Triple(ErrorCode.BANNED, 403, BannedException::class),
                Triple(ErrorCode.NOT_FOUND, 404, NotFoundException::class),
                Triple(ErrorCode.CONFLICT, 409, ConflictException::class),
                Triple(ErrorCode.GONE, 410, GoneException::class),
                Triple(ErrorCode.UNSUPPORTED_MEDIA, 415, UnsupportedMediaException::class),
                Triple(ErrorCode.RATE_LIMITED, 429, RateLimitException::class),
                Triple(ErrorCode.INTERNAL, 500, InternalServerException::class),
            )

        for ((code, status, expectedClass) in testCases) {
            val json =
                """
                {
                    "code": "${code.name}",
                    "status": $status,
                    "detail": "Test error for ${code.name}",
                    "request_id": "req_test_${code.name.lowercase()}"
                }
                """.trimIndent()

            val response = mockResponse(status)
            val exception = createApiException(response, json)

            assertTrue(
                expectedClass.isInstance(exception),
                "Expected ${expectedClass.simpleName} for code ${code.name}, but got ${exception::class.simpleName}",
            )
            assertEquals(status, exception.status)
            assertEquals(code, exception.code)
            assertEquals("Test error for ${code.name}", exception.detail)
            assertEquals("req_test_${code.name.lowercase()}", exception.requestId)
        }
    }

    @Test
    fun testFallbackToStatusBasedSubclassWhenJsonEmptyOrMalformed() {
        val empty404 = createApiException(mockResponse(404), "")
        assertTrue(empty404 is NotFoundException)
        assertEquals(404, empty404.status)
        assertEquals(ErrorCode.NOT_FOUND, empty404.code)

        val empty410 = createApiException(mockResponse(410), null)
        assertTrue(empty410 is GoneException)
        assertEquals(410, empty410.status)
        assertEquals(ErrorCode.GONE, empty410.code)

        val html502 = createApiException(mockResponse(502), "<html>502 Bad Gateway</html>")
        assertTrue(html502 is InternalServerException)
        assertEquals(502, html502.status)
        assertEquals(ErrorCode.INTERNAL, html502.code)
    }

    @Test
    fun testUnknownCodeReturnsBaseActosApiExceptionWithNullCode() {
        val json =
            """
            {
                "code": "FUTURE_UNKNOWN_ERROR_CODE",
                "status": 418,
                "detail": "I am a teapot",
                "request_id": "req_teapot_01"
            }
            """.trimIndent()

        val response = mockResponse(418)
        val exception = createApiException(response, json)

        assertEquals(ActosApiException::class, exception::class)
        assertNull(exception.code)
        assertEquals(418, exception.status)
        assertEquals("I am a teapot", exception.detail)
        assertEquals("req_teapot_01", exception.requestId)
        assertTrue(exception.message!!.contains("[418 HTTP_418] I am a teapot (requestId=req_teapot_01)"))
    }

    @Test
    fun testMessageFormatting() {
        val json =
            """
            {
                "code": "NOT_FOUND",
                "status": 404,
                "detail": "Post not found",
                "request_id": "01a0bcdef"
            }
            """.trimIndent()

        val response = mockResponse(404)
        val exception = createApiException(response, json)
        assertEquals("[404 NOT_FOUND] Post not found (requestId=01a0bcdef)", exception.message)

        val fallback = createApiException(mockResponse(500), null)
        assertEquals("[500 INTERNAL] HTTP 500 error (requestId=none)", fallback.message)
    }

    @Test
    fun testRateLimitExceptionCarriesRetryAfterAndRateLimit() {
        val headers =
            Headers.headersOf(
                "x-ratelimit-limit", "60",
                "x-ratelimit-remaining", "0",
                "x-ratelimit-reset", "1700001000",
                "retry-after", "15",
                "x-request-id", "req_rl_99",
            )
        val json =
            """
            {
                "code": "RATE_LIMITED",
                "status": 429,
                "detail": "Rate limit exceeded. Try again in 15 seconds."
            }
            """.trimIndent()

        val response = mockResponse(429, headers)
        val exception = createApiException(response, json)

        assertTrue(exception is RateLimitException)
        val rlEx = exception as RateLimitException
        assertEquals(15L, rlEx.retryAfter)
        assertNotNull(rlEx.rateLimit)
        assertEquals(60, rlEx.rateLimit!!.limit)
        assertEquals(0, rlEx.rateLimit!!.remaining)
        assertEquals("req_rl_99", rlEx.requestId)
    }

    @Test
    fun testConveniencePredicates() {
        val notFound = NotFoundException()
        assertTrue(notFound.isNotFound)
        assertFalse(notFound.isGone)
        assertFalse(notFound.isRateLimited)
        assertFalse(notFound.isForbidden)
        assertFalse(notFound.isRetryable)

        val gone = GoneException()
        assertTrue(gone.isGone)
        assertFalse(gone.isNotFound)

        val rateLimited = RateLimitException()
        assertTrue(rateLimited.isRateLimited)
        assertTrue(rateLimited.isRetryable)

        val forbidden = ForbiddenException()
        assertTrue(forbidden.isForbidden)
        val banned = BannedException()
        assertTrue(banned.isForbidden)

        val internal = InternalServerException()
        assertTrue(internal.isRetryable)

        val timeout = ApiTimeoutException("timeout")
        assertTrue(timeout.isRetryable)

        val conn = ApiConnectionException("conn")
        assertTrue(conn.isRetryable)
    }

    @Test
    fun testMockWebServerThrowsTypedExceptions() =
        runTest {
            val transport = Transport(baseUrl = server.url("/"), maxRetries = 0)

            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"NOT_FOUND","status":404,"detail":"Post not found"}"""),
            )
            val notFoundEx =
                assertThrows<NotFoundException> {
                    transport.get("/posts/p_missing")
                }
            assertEquals(404, notFoundEx.status)
            assertTrue(notFoundEx.isNotFound)

            server.enqueue(
                MockResponse()
                    .setResponseCode(410)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"GONE","status":410,"detail":"Post was deleted"}"""),
            )
            val goneEx =
                assertThrows<GoneException> {
                    transport.get("/posts/p_deleted")
                }
            assertEquals(410, goneEx.status)
            assertTrue(goneEx.isGone)

            server.enqueue(
                MockResponse()
                    .setResponseCode(403)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"BANNED","status":403,"detail":"You are banned"}"""),
            )
            val bannedEx =
                assertThrows<BannedException> {
                    transport.get("/me")
                }
            assertTrue(bannedEx.isForbidden)
        }

    @Test
    fun testTimeoutThrowsApiTimeoutException() =
        runTest {
            val shortTimeoutClient =
                OkHttpClient.Builder()
                    .connectTimeout(50, TimeUnit.MILLISECONDS)
                    .readTimeout(50, TimeUnit.MILLISECONDS)
                    .writeTimeout(50, TimeUnit.MILLISECONDS)
                    .build()

            val transport =
                Transport(
                    baseUrl = server.url("/"),
                    maxRetries = 0,
                    okHttpClient = shortTimeoutClient,
                )

            server.enqueue(
                MockResponse()
                    .setHeadersDelay(500, TimeUnit.MILLISECONDS)
                    .setBody("{}"),
            )

            assertThrows<ApiTimeoutException> {
                transport.get("/timeout")
            }
        }
}
