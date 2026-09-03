package dev.actos.resources

import dev.actos.Actos
import dev.actos.ActosApiException
import dev.actos.UploadSource
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
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

class UploadsTest {
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

    private fun uploadResponseJson(
        id: String = "up_123",
        url: String = "https://cdn.actos.dev/up_123.webp",
    ): String =
        """
        {
            "id": "$id",
            "url": "$url",
            "thumbnail_url": "$url",
            "mime_type": "image/webp",
            "byte_size": 2048,
            "checksum_sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "width": 800,
            "height": 600,
            "created_at": "2026-09-01T00:00:00Z"
        }
        """.trimIndent()

    private fun postJson(
        id: String = "p_1",
        attachmentId: String = "up_123",
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
            "body": "Post with image",
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
            "title": "Post Title",
            "attachments": [${uploadResponseJson(id = attachmentId)}]
        }
        """.trimIndent()

    @Test
    fun testCreateWithByteArray() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(uploadResponseJson()))

            val bytes = "Fake PNG payload".toByteArray()
            val response =
                client.uploads().create(
                    UploadSource.FromBytes(
                        bytes = bytes,
                        filename = "image.png",
                        contentType = "image/png",
                    ),
                )

            assertEquals("up_123", response.id)
            assertEquals("image/webp", response.mimeType)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("POST", rec!!.method)
            assertEquals("/uploads", rec.path)
            assertTrue(rec.getHeader("Content-Type")?.startsWith("multipart/form-data") == true)
            val body = rec.body.readUtf8()
            assertTrue(body.contains("name=\"file\"; filename=\"image.png\""))
            assertTrue(body.contains("Fake PNG payload"))
        }

    @Test
    fun testCreateWithFile() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(uploadResponseJson()))

            val tempFile = File.createTempFile("test_upload", ".jpg")
            try {
                tempFile.writeText("JPEG image data")
                val response = client.uploads().create(tempFile)

                assertEquals("up_123", response.id)

                val rec = server.takeRequest(5, TimeUnit.SECONDS)
                assertNotNull(rec)
                assertEquals("/uploads", rec!!.path)
                val body = rec.body.readUtf8()
                assertTrue(body.contains(tempFile.name))
                assertTrue(body.contains("JPEG image data"))
            } finally {
                tempFile.delete()
            }
        }

    @Test
    fun testCreateWithInputStreamStreaming() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(201).setBody(uploadResponseJson()))

            val stream = ByteArrayInputStream("Streaming chunk data".toByteArray())
            val response =
                client.uploads().create(
                    stream = stream,
                    filename = "stream.bin",
                    byteLength = 20L,
                )

            assertEquals("up_123", response.id)

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            val body = rec!!.body.readUtf8()
            assertTrue(body.contains("Streaming chunk data"))
        }

    @Test
    fun testDeleteUpload() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))

            client.uploads().delete("up_old_file")

            val rec = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(rec)
            assertEquals("DELETE", rec!!.method)
            assertEquals("/uploads/up_old_file", rec.path)
        }

    @Test
    fun testPayloadTooLargeThrowsUnsupportedMediaOrApiException() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(413)
                    .setHeader("Content-Type", "application/problem+json")
                    .setBody(
                        """{"code":"PAYLOAD_TOO_LARGE","status":413,"detail":"File exceeds maximum upload size"}""",
                    ),
            )

            val ex =
                assertThrows<ActosApiException> {
                    client.uploads().create("Huge binary data".toByteArray())
                }
            assertEquals(413, ex.status)
            assertEquals("File exceeds maximum upload size", ex.detail)
        }

    @Test
    fun testFullUploadAndAttachToPostFlow() =
        runTest {
            // Step 1: Upload image
            server.enqueue(MockResponse().setResponseCode(201).setBody(uploadResponseJson(id = "up_pic_42")))
            val upload = client.uploads().create("pic content".toByteArray(), filename = "banner.png")
            assertEquals("up_pic_42", upload.id)

            val recUpload = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("/uploads", recUpload!!.path)

            // Step 2: Attach to post
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(postJson(id = "p_with_banner", attachmentId = "up_pic_42")),
            )
            val post =
                client.posts().create(
                    title = "Post with attachment",
                    body = "Check out this image!",
                    attachmentIds = listOf(upload.id),
                )

            assertEquals("p_with_banner", post.id)
            assertNotNull(post.attachments)
            assertEquals(1, post.attachments?.size)
            assertEquals("up_pic_42", post.attachments?.get(0)?.id)

            val recPost = server.takeRequest(5, TimeUnit.SECONDS)
            assertEquals("/posts", recPost!!.path)
            assertTrue(recPost.body.readUtf8().contains("\"attachment_ids\":[\"up_pic_42\"]"))
        }
}
