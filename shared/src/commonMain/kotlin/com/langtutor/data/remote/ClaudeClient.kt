package com.langtutor.data.remote

import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.ClaudeResponse

/**
 * Thin transport layer for the Anthropic Messages API.
 * [send] sends a fully-built request and returns the response.
 * [validateKey] calls GET /v1/models (no tokens consumed) to verify a key.
 * All business logic lives in use cases and PromptBuilder, not here.
 */
interface ClaudeClient {
    suspend fun send(request: ClaudeRequest): Result<ClaudeResponse>
    suspend fun validateKey(key: String): Result<Unit>
}
