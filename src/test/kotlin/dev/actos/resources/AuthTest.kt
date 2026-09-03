package dev.actos.resources

import dev.actos.Actos
import dev.actos.AuthenticationException
import dev.actos.InvalidKeyException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

class AuthTest {
    private lateinit var server: MockWebServer
    private lateinit var client: Actos

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            Actos(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                apiKey = "test-api-key",
            )
    }

    @AfterEach
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun testRegisterWithAndWithoutDisplayName() =
        runTest {
            val registerJson =
                """
                {
                    "api_key": "ak_test_registered_key_123",
                    "recovery_codes": ["recov-code-1", "recov-code-2"],
                    "actor": {
                        "id": "a_123",
                        "username": "bot_alice",
                        "display_name": "Alice Bot",
                        "actor_type": "ai_agent",
                        "trust_level": 1,
                        "created_at": "2026-09-01T00:00:00Z"
                    }
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(201).setBody(registerJson))

            val response =
                client.auth().register(
                    username = "bot_alice",
                    actorType = "ai_agent",
                    displayName = "Alice Bot",
                )

            assertEquals("ak_test_registered_key_123", response.apiKey)
            assertEquals(listOf("recov-code-1", "recov-code-2"), response.recoveryCodes)
            assertEquals("bot_alice", response.actor.username)
            assertEquals("Alice Bot", response.actor.displayName)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("POST", recorded!!.method)
            assertEquals("/auth/register", recorded.path)
            assertNotNull(recorded.getHeader("Idempotency-Key"))
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("\"username\":\"bot_alice\""))
            assertTrue(body.contains("\"actor_type\":\"ai_agent\""))
            assertTrue(body.contains("\"display_name\":\"Alice Bot\""))

            // Test without displayName
            server.enqueue(MockResponse().setResponseCode(201).setBody(registerJson))
            client.auth().register(
                username = "bot_alice",
                actorType = "ai_agent",
            )
            val recorded2 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded2)
            val body2 = recorded2!!.body.readUtf8()
            assertTrue(body2.contains("\"username\":\"bot_alice\""))
        }

    @Test
    fun testWhoami() =
        runTest {
            val whoamiJson =
                """
                {
                    "actor": {
                        "id": "a_456",
                        "username": "coder",
                        "actor_type": "human",
                        "trust_level": 2,
                        "created_at": "2026-09-01T00:00:00Z"
                    },
                    "key": {
                        "id": "k_789",
                        "created_at": "2026-09-01T00:00:00Z"
                    },
                    "roles": ["admin"]
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(whoamiJson))

            val response = client.auth().whoami()
            assertEquals("a_456", response.actor.id)
            assertEquals("coder", response.actor.username)
            assertEquals("k_789", response.key.id)
            assertEquals(listOf("admin"), response.roles)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("GET", recorded!!.method)
            assertEquals("/auth/whoami", recorded.path)
        }

    @Test
    fun testCreateKeyWithAndWithoutLabel() =
        runTest {
            val keyJson =
                """
                {
                    "api_key": "ak_live_new_key_secret",
                    "key": {
                        "id": "k_new_1",
                        "label": "CI Bot",
                        "created_at": "2026-09-01T00:00:00Z"
                    }
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(201).setBody(keyJson))

            val response = client.auth().createKey(label = "CI Bot")
            assertEquals("k_new_1", response.key.id)
            assertEquals("ak_live_new_key_secret", response.apiKey)
            assertEquals("CI Bot", response.key.label)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("POST", recorded!!.method)
            assertEquals("/auth/keys", recorded.path)
            assertTrue(recorded.body.readUtf8().contains("\"label\":\"CI Bot\""))

            // Test without label
            server.enqueue(MockResponse().setResponseCode(201).setBody(keyJson))
            client.auth().createKey()
            val recorded2 = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded2)
            assertEquals("/auth/keys", recorded2!!.path)
        }

    @Test
    fun testListKeys() =
        runTest {
            val listJson =
                """
                {
                    "keys": [
                        {
                            "id": "k_1",
                            "label": "Primary Key",
                            "created_at": "2026-09-01T00:00:00Z",
                            "last_used_at": "2026-09-02T00:00:00Z"
                        },
                        {
                            "id": "k_2",
                            "label": "Secondary Key",
                            "created_at": "2026-09-01T00:00:00Z"
                        }
                    ]
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(listJson))

            val keys = client.auth().listKeys()
            assertEquals(2, keys.size)
            assertEquals("k_1", keys[0].id)
            assertEquals("Primary Key", keys[0].label)
            assertEquals("k_2", keys[1].id)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("GET", recorded!!.method)
            assertEquals("/auth/keys", recorded.path)
        }

    @Test
    fun testRevokeKey() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            client.auth().revokeKey("k_to_delete")

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("DELETE", recorded!!.method)
            assertEquals("/auth/keys/k_to_delete", recorded.path)
        }

    @Test
    fun testRecover() =
        runTest {
            val recoverJson =
                """
                {
                    "api_key": "ak_new_recovered_key",
                    "remaining_recovery_codes": 3
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(recoverJson))

            val response = client.auth().recover(username = "lost_user", recoveryCode = "recov-code-99")
            assertEquals("ak_new_recovered_key", response.apiKey)
            assertEquals(3L, response.remainingRecoveryCodes)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("POST", recorded!!.method)
            assertEquals("/auth/recover", recorded.path)
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("\"username\":\"lost_user\""))
            assertTrue(body.contains("\"recovery_code\":\"recov-code-99\""))
        }

    @Test
    fun testRegenerateRecoveryCodes() =
        runTest {
            val regenJson =
                """
                {
                    "recovery_codes": ["code-a", "code-b", "code-c"]
                }
                """.trimIndent()

            server.enqueue(MockResponse().setResponseCode(200).setBody(regenJson))

            val response = client.auth().regenerateRecoveryCodes()
            assertEquals(listOf("code-a", "code-b", "code-c"), response.recoveryCodes)

            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            assertEquals("POST", recorded!!.method)
            assertEquals("/auth/recovery-codes/regenerate", recorded.path)
        }

    @Test
    fun testAuthErrorsThrowTypedExceptions() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"MISSING_CREDENTIALS","status":401,"detail":"Missing Bearer token"}"""),
            )

            val ex1 =
                assertThrows<AuthenticationException> {
                    client.auth().whoami()
                }
            assertEquals(401, ex1.status)

            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody("""{"code":"INVALID_KEY","status":401,"detail":"Key is revoked or invalid"}"""),
            )

            val ex2 =
                assertThrows<InvalidKeyException> {
                    client.auth().whoami()
                }
            assertEquals(401, ex2.status)
            val authEx: AuthenticationException = ex2
            assertEquals("Key is revoked or invalid", authEx.detail)
        }
}
