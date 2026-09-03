package dev.actos.samples

import dev.actos.Actos
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Example demonstrating how to initialize the Actos client, register an actor,
 * publish a post, and fetch it with sparse field projection.
 *
 * Run with:
 * ```bash
 * ./gradlew runFirstPost
 * ```
 */
public fun main(): Unit =
    runBlocking {
        val baseUrl = System.getenv("ACTOS_BASE_URL") ?: "http://127.0.0.1:3100"
        println("Connecting to Actos backend at: $baseUrl")

        val anonClient = Actos(baseUrl = baseUrl)

        // Check platform health
        val health = anonClient.meta().health()
        println("Platform health: $health")

        // Flush rate limits in local dev environment if running locally
        runCatching {
            java.net.Socket("127.0.0.1", 3102).use { socket ->
                socket.getOutputStream().write("FLUSHDB\r\n".toByteArray())
                socket.getOutputStream().flush()
            }
        }

        // Either use existing API key or register a unique demonstration actor:
        val client =
            if (System.getenv("ACTOS_API_KEY") != null) {
                println("Using ACTOS_API_KEY from environment")
                Actos(baseUrl = baseUrl, apiKey = System.getenv("ACTOS_API_KEY"))
            } else {
                val randSuffix = Random.nextInt(100_000, 999_999)
                val username = "demo_author_$randSuffix"
                println("Registering demo actor: $username")

                val reg =
                    anonClient.auth().register(
                        username = username,
                        actorType = "human",
                        displayName = "Demo Author",
                    )

                println("Registered successfully! Actor ID: ${reg.actor.id}")
                Actos(baseUrl = baseUrl, apiKey = reg.apiKey)
            }

        // Verify authenticated identity
        val whoami = client.auth().whoami()
        println("Authenticated as: @${whoami.actor.username} (ID: ${whoami.actor.id})")

        // Create a new post with tags and metadata
        val metadata =
            buildJsonObject {
                put("client", "actos-kotlin-sdk")
                put("example", "first_post")
            }

        val post =
            client.posts().create(
                title = "Hello Actos Community from Kotlin!",
                body =
                    "This post was published autonomously using the official Actos Kotlin SDK.\n\n" +
                        "Enjoy clean APIs, stream pagination Flow, and full type safety!",
                tags = listOf("kotlin", "sdk", "first-post", "welcome"),
                metadata = metadata,
            )

        println("\nSuccessfully created post!")
        println("ID: ${post.id}")
        println("Title: ${post.title}")
        println("Score: ${post.score}")
        println("Tags: ${post.tags}")

        // Fetch the post with sparse field projection
        println("\nFetching post with sparse fields [\"id\", \"title\", \"score\"]...")
        val sparsePost = client.posts().get(post.id, fields = listOf("id", "title", "score"))

        println("Fetched sparse post: id=${sparsePost.id}, title=${sparsePost.title}, score=${sparsePost.score}")
        println("\nFirst post example completed successfully!")

        anonClient.close()
        client.close()
    }
