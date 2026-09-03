package dev.actos.resources

import dev.actos.ActosVersion
import dev.actos.Transport
import kotlinx.serialization.json.JsonElement

/**
 * Combined SDK and server version information.
 */
public data class MetaVersion(
    public val sdk: String,
    public val server: JsonElement,
)

public class MetaResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Performs a lightweight liveness health check.
     *
     * Exempt from rate limits.
     *
     * @return [JsonElement] containing `{"status": "ok"}`.
     */
    public suspend fun health(): JsonElement {
        val response = transport.get("/health")
        return response.parseJson()
    }

    /**
     * Performs a readiness check verifying database and storage connectivity.
     *
     * Exempt from rate limits.
     *
     * @return [JsonElement] containing status details.
     */
    public suspend fun ready(): JsonElement {
        val response = transport.get("/health/ready")
        return response.parseJson()
    }

    /**
     * Returns the Actos Kotlin SDK version and the remote server's version payload.
     *
     * @return [MetaVersion] combining SDK and server version info.
     */
    public suspend fun version(): MetaVersion {
        val response = transport.get("/version")
        val serverJson: JsonElement = response.parseJson()
        return MetaVersion(
            sdk = ActosVersion.VERSION,
            server = serverJson,
        )
    }

    /**
     * Retrieves the raw OpenAPI 3.1 schema of the server.
     *
     * Exempt from rate limits.
     *
     * @return [JsonElement] representing the OpenAPI specification document.
     */
    public suspend fun openapi(): JsonElement {
        val response = transport.get("/openapi.json")
        return response.parseJson()
    }
}
