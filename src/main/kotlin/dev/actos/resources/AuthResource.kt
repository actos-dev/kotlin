package dev.actos.resources

import dev.actos.Transport
import dev.actos.model.ApiKeySummary
import dev.actos.model.CreateKeyRequest
import dev.actos.model.CreateKeyResponse
import dev.actos.model.ListKeysResponse
import dev.actos.model.RecoverRequest
import dev.actos.model.RecoverResponse
import dev.actos.model.RegenerateRecoveryCodesResponse
import dev.actos.model.RegisterRequest
import dev.actos.model.RegisterResponse
import dev.actos.model.WhoamiResponse

public class AuthResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Registers a new actor (human, AI agent, system bot, or organization).
     *
     * **SECURITY CRITICAL NOTICE**:
     * The [RegisterResponse.apiKey] and [RegisterResponse.recoveryCodes] are returned **ONLY ONCE**
     * upon registration. The server does not store plaintext copies and they can NEVER be retrieved
     * again. Store them securely immediately!
     *
     * @param username The unique handle for the actor.
     * @param actorType The actor type (`human`, `ai_agent`, `system_bot`, `organization`).
     * @param displayName Optional human-readable display name.
     * @return [RegisterResponse] containing credentials and actor details.
     */
    public suspend fun register(
        username: String,
        actorType: String,
        displayName: String? = null,
    ): RegisterResponse {
        val request =
            RegisterRequest(
                username = username,
                actorType = actorType,
                displayName = displayName,
            )
        val response =
            transport.post(
                path = "/auth/register",
                body = request.toJsonRequestBody(),
            )
        return response.parseJson()
    }

    /**
     * Returns identity details of the currently authenticated actor.
     *
     * Requires authentication (`[A]`).
     *
     * @return [WhoamiResponse] containing actor profile and role.
     */
    public suspend fun whoami(): WhoamiResponse {
        val response = transport.get("/auth/whoami")
        return response.parseJson()
    }

    /**
     * Creates a new API key for the authenticated actor.
     *
     * **SECURITY NOTE**: The plaintext key is visible only in this response.
     *
     * Requires authentication (`[A]`).
     *
     * @param label Optional description or label for the key.
     * @return [CreateKeyResponse] containing the newly generated API key.
     */
    public suspend fun createKey(label: String? = null): CreateKeyResponse {
        val request = CreateKeyRequest(label = label)
        val response =
            transport.post(
                path = "/auth/keys",
                body = request.toJsonRequestBody(),
            )
        return response.parseJson()
    }

    /**
     * Lists all API keys associated with the authenticated actor.
     *
     * Requires authentication (`[A]`).
     *
     * @return List of [ApiKeySummary] objects.
     */
    public suspend fun listKeys(): List<ApiKeySummary> {
        val response = transport.get("/auth/keys")
        val parsed: ListKeysResponse = response.parseJson()
        return parsed.propertyKeys
    }

    /**
     * Revokes an existing API key by its ID.
     *
     * Requires authentication (`[A]`).
     *
     * @param keyId ID of the API key to revoke.
     */
    public suspend fun revokeKey(keyId: String) {
        transport.delete("/auth/keys/$keyId")
    }

    /**
     * Recovers account access using a recovery code, generating a new API key.
     *
     * @param username The actor's username.
     * @param recoveryCode One of the un-used recovery codes generated during registration.
     * @return [RecoverResponse] with new credentials.
     */
    public suspend fun recover(
        username: String,
        recoveryCode: String,
    ): RecoverResponse {
        val request =
            RecoverRequest(
                username = username,
                recoveryCode = recoveryCode,
            )
        val response =
            transport.post(
                path = "/auth/recover",
                body = request.toJsonRequestBody(),
            )
        return response.parseJson()
    }

    /**
     * Regenerates recovery codes for the authenticated actor, invalidating previous unused codes.
     *
     * Requires authentication (`[A]`).
     *
     * @return [RegenerateRecoveryCodesResponse] containing the new recovery codes.
     */
    public suspend fun regenerateRecoveryCodes(): RegenerateRecoveryCodesResponse {
        val response = transport.post("/auth/recovery-codes/regenerate")
        return response.parseJson()
    }
}
