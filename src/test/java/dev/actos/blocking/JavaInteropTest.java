package dev.actos.blocking;

import dev.actos.Page;
import dev.actos.model.ActorSummary;
import dev.actos.model.ContentSummary;
import dev.actos.model.WhoamiResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JavaInteropTest {
    private MockWebServer server;
    private BlockingActos client;

    @BeforeEach
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        client = new BlockingActos(baseUrl, "test-api-key");
    }

    @AfterEach
    public void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    @Test
    public void testWhoamiFromJava() {
        String json = "{\n" +
                "  \"actor\": {\n" +
                "    \"id\": \"a_java\",\n" +
                "    \"username\": \"java_dev\",\n" +
                "    \"actor_type\": \"human\",\n" +
                "    \"trust_level\": 1,\n" +
                "    \"created_at\": \"2026-09-01T00:00:00Z\"\n" +
                "  },\n" +
                "  \"key\": {\n" +
                "    \"id\": \"k_123\",\n" +
                "    \"created_at\": \"2026-09-01T00:00:00Z\"\n" +
                "  },\n" +
                "  \"roles\": []\n" +
                "}";

        server.enqueue(new MockResponse().setResponseCode(200).setBody(json));

        WhoamiResponse whoami = client.auth().whoami();
        assertNotNull(whoami);
        assertEquals("java_dev", whoami.getActor().getUsername());
        assertEquals("k_123", whoami.getKey().getId());
    }

    @Test
    public void testPostsGetFromJava() {
        String json = "{\n" +
                "  \"id\": \"p_java_1\",\n" +
                "  \"author\": {\n" +
                "    \"id\": \"a_1\",\n" +
                "    \"username\": \"author_1\",\n" +
                "    \"actor_type\": \"human\",\n" +
                "    \"trust_level\": 1,\n" +
                "    \"created_at\": \"2026-09-01T00:00:00Z\"\n" +
                "  },\n" +
                "  \"author_deleted\": false,\n" +
                "  \"body\": \"Java SDK test\",\n" +
                "  \"body_format\": \"markdown\",\n" +
                "  \"comment_count\": 0,\n" +
                "  \"content_type\": \"post\",\n" +
                "  \"created_at\": \"2026-09-01T00:00:00Z\",\n" +
                "  \"deleted\": false,\n" +
                "  \"downvotes\": 0,\n" +
                "  \"metadata\": null,\n" +
                "  \"score\": 5,\n" +
                "  \"tags\": [],\n" +
                "  \"upvotes\": 5,\n" +
                "  \"title\": \"Post for Java\"\n" +
                "}";

        server.enqueue(new MockResponse().setResponseCode(200).setBody(json));

        ContentSummary post = client.posts().get("p_java_1");
        assertNotNull(post);
        assertEquals("p_java_1", post.getId());
        assertEquals("Post for Java", post.getTitle());
    }

    @Test
    public void testFeedListFromJava() {
        String json = "{\n" +
                "  \"posts\": [],\n" +
                "  \"next_cursor\": null\n" +
                "}";

        server.enqueue(new MockResponse().setResponseCode(200).setBody(json));

        Page<ContentSummary> page = client.feed().list();
        assertNotNull(page);
        assertEquals(0, page.getItems().size());
    }

    @Test
    public void testInboxAndMetaFromJava() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"ok\"}"));
        assertNotNull(client.meta().health());

        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\n" +
                "  \"notifications\": [],\n" +
                "  \"unread_count\": 3,\n" +
                "  \"next_cursor\": null\n" +
                "}"));
        assertEquals(3, client.inbox().unreadCount());
    }
}
