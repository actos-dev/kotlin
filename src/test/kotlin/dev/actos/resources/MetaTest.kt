package dev.actos.resources

import dev.actos.Actos
import dev.actos.ActosVersion
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class MetaTest {
    private lateinit var server: MockWebServer
    private lateinit var client: Actos

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            Actos(
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun testHealth() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))

            val health = client.meta().health()
            assertEquals("ok", health.jsonObject["status"]?.jsonPrimitive?.content)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            assertEquals("/health", rec.path)
        }

    @Test
    fun testReady() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("""{"database":"connected","storage":"ready"}"""),
            )

            val ready = client.meta().ready()
            assertEquals("connected", ready.jsonObject["database"]?.jsonPrimitive?.content)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            assertEquals("/health/ready", rec.path)
        }

    @Test
    fun testVersionCombinesSdkAndServerVersion() =
        runTest {
            val serverVersionJson =
                """
                {
                    "name": "actos-api",
                    "version": "0.1.0",
                    "api_version": "0.1.0",
                    "git_sha": "abc1234"
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(serverVersionJson))

            val version = client.meta().version()
            assertEquals(ActosVersion.VERSION, version.sdk)
            assertEquals("actos-api", version.server.jsonObject["name"]?.jsonPrimitive?.content)
            assertEquals("abc1234", version.server.jsonObject["git_sha"]?.jsonPrimitive?.content)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            assertEquals("/version", rec.path)
        }

    @Test
    fun testOpenApi() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200).setBody("""{"openapi":"3.1.0","info":{"title":"Actos"}}"""),
            )

            val openapi = client.meta().openapi()
            assertEquals("3.1.0", openapi.jsonObject["openapi"]?.jsonPrimitive?.content)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("GET", rec!!.method)
            assertEquals("/openapi.json", rec.path)
        }

    @Test
    fun testRateLimitAccessOnClient() =
        runTest {
            // Before any calls with rate limit headers, rateLimit is null
            assertNull(client.rateLimit)

            // Make a call with rate limit headers
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("X-RateLimit-Limit", "120")
                    .setHeader("X-RateLimit-Remaining", "119")
                    .setHeader("X-RateLimit-Reset", "1725000000")
                    .setBody("""{"status":"ok"}"""),
            )

            client.meta().health()

            val rl = client.rateLimit
            assertNotNull(rl)
            assertEquals(120, rl!!.limit)
            assertEquals(119, rl.remaining)
            assertEquals(1725000000L, rl.reset)
        }
}
