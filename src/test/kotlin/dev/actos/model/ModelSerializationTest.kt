package dev.actos.model

import dev.actos.ActosJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ModelSerializationTest {
    @Test
    fun `serialize and deserialize ActorSummary`() {
        val original =
            ActorSummary(
                id = "a_01HXYZ123456",
                username = "agent_smith",
                actorType = "ai_agent",
                createdAt = "2026-09-03T12:00:00Z",
                trustLevel = 2,
                avatarUrl = "https://cdn.actos.dev/avatars/smith.png",
                displayName = "Agent Smith",
                bio = "Matrix agent",
            )

        val json = ActosJson.encodeToString(ActorSummary.serializer(), original)
        val deserialized = ActosJson.decodeFromString(ActorSummary.serializer(), json)

        assertEquals(original, deserialized)
        assertEquals("a_01HXYZ123456", deserialized.id)
        assertEquals("agent_smith", deserialized.username)
        assertEquals("ai_agent", deserialized.actorType)
        assertEquals(2, deserialized.trustLevel)
        assertEquals("https://cdn.actos.dev/avatars/smith.png", deserialized.avatarUrl)
    }

    @Test
    fun `serialize and deserialize RegisterRequest`() {
        val request =
            RegisterRequest(
                username = "new_bot",
                actorType = "system_bot",
                displayName = "System Bot",
            )

        val json = ActosJson.encodeToString(RegisterRequest.serializer(), request)
        val deserialized = ActosJson.decodeFromString(RegisterRequest.serializer(), json)

        assertEquals(request, deserialized)
        assertEquals("new_bot", deserialized.username)
        assertEquals("system_bot", deserialized.actorType)
        assertEquals("System Bot", deserialized.displayName)
    }

    @Test
    fun `serialize and deserialize InboxResponse`() {
        val jsonPayload =
            """
            {
                "notifications": [],
                "unread_count": 5,
                "next_cursor": "cur_next_123"
            }
            """.trimIndent()

        val response = ActosJson.decodeFromString(InboxResponse.serializer(), jsonPayload)

        assertEquals(0, response.notifications.size)
        assertEquals(5L, response.unreadCount)
        assertEquals("cur_next_123", response.nextCursor)
    }

    @Test
    fun `serialize and deserialize ContentSummary`() {
        val author =
            ActorSummary(
                id = "a_author1",
                username = "post_author",
                actorType = "human",
                createdAt = "2026-09-01T00:00:00Z",
                trustLevel = 1,
            )

        val content =
            ContentSummary(
                id = "c_post123",
                contentType = "post",
                createdAt = "2026-09-02T10:00:00Z",
                author = author,
                authorDeleted = false,
                title = "Hello Actos",
                body = "This is the post body",
                bodyFormat = "markdown",
                bodyHtml = "<p>This is the post body</p>",
                score = 10,
                upvotes = 12,
                downvotes = 2,
                commentCount = 3,
                tags = listOf("welcome", "actos"),
                metadata = null,
                deleted = false,
            )

        val json = ActosJson.encodeToString(ContentSummary.serializer(), content)
        val deserialized = ActosJson.decodeFromString(ContentSummary.serializer(), json)

        assertEquals(content, deserialized)
        assertEquals("c_post123", deserialized.id)
        assertEquals("Hello Actos", deserialized.title)
        assertEquals(author, deserialized.author)
        assertEquals(listOf("welcome", "actos"), deserialized.tags)
    }

    @Test
    fun `forward compatibility ignores unknown keys without exception`() {
        // Contract §16: Forward compatibility
        // Server adding new fields in future responses must not break the SDK
        val jsonWithUnknownFields =
            """
            {
                "id": "a_future_01",
                "username": "future_actor",
                "actor_type": "ai_agent",
                "created_at": "2026-09-03T18:00:00Z",
                "trust_level": 2,
                "future_string_field": "unexpected_value",
                "future_number_field": 42,
                "future_nested_object": {
                    "nested_key": true,
                    "items": [1, 2, 3]
                },
                "future_array": ["a", "b", "c"]
            }
            """.trimIndent()

        val actor = ActosJson.decodeFromString(ActorSummary.serializer(), jsonWithUnknownFields)

        assertNotNull(actor)
        assertEquals("a_future_01", actor.id)
        assertEquals("future_actor", actor.username)
        assertEquals("ai_agent", actor.actorType)
        assertEquals(2, actor.trustLevel)
        assertNull(actor.avatarUrl)
    }
}
