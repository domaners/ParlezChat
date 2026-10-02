package com.langtutor.test

import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.remote.ClaudeError
import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.ClaudeResponse
import com.langtutor.data.remote.dto.ContentBlockDto
import com.langtutor.data.remote.dto.UsageDto

/**
 * Test double for [ClaudeClient]. Configure [nextResult] before each call.
 * Records all requests sent through [requests].
 */
class FakeClaudeClient : ClaudeClient {

    val requests = mutableListOf<ClaudeRequest>()
    var nextResult: Result<ClaudeResponse> = Result.success(textResponse("Hello!"))
    var validateResult: Result<Unit> = Result.success(Unit)
    var callCount = 0

    /** Optional sequence of per-call results, consumed in order before falling back to [nextResult]. */
    val resultsQueue = ArrayDeque<Result<ClaudeResponse>>()

    override suspend fun send(request: ClaudeRequest): Result<ClaudeResponse> {
        requests.add(request)
        callCount++
        return if (resultsQueue.isNotEmpty()) resultsQueue.removeFirst() else nextResult
    }

    override suspend fun validateKey(key: String): Result<Unit> = validateResult

    fun reset() {
        requests.clear()
        callCount = 0
        nextResult = Result.success(textResponse("Hello!"))
        validateResult = Result.success(Unit)
    }

    companion object {
        fun textResponse(text: String) = ClaudeResponse(
            id = "msg_test",
            type = "message",
            role = "assistant",
            content = listOf(ContentBlockDto(type = "text", text = text)),
            stopReason = "end_turn",
            usage = UsageDto(inputTokens = 10, outputTokens = 5),
        )

        fun toolResponse(toolName: String, inputJson: String): ClaudeResponse {
            val input = kotlinx.serialization.json.Json.parseToJsonElement(inputJson)
            return ClaudeResponse(
                id = "msg_test",
                type = "message",
                role = "assistant",
                content = listOf(
                    ContentBlockDto(type = "tool_use", id = "toolu_test", name = toolName, input = input)
                ),
                stopReason = "tool_use",
                usage = UsageDto(inputTokens = 10, outputTokens = 50),
            )
        }

        /** A response containing both a text reply and a tool_use block, as the model may emit in one turn. */
        fun mixedResponse(text: String, toolName: String, inputJson: String): ClaudeResponse {
            val input = kotlinx.serialization.json.Json.parseToJsonElement(inputJson)
            return ClaudeResponse(
                id = "msg_test",
                type = "message",
                role = "assistant",
                content = listOf(
                    ContentBlockDto(type = "text", text = text),
                    ContentBlockDto(type = "tool_use", id = "toolu_test", name = toolName, input = input),
                ),
                stopReason = "tool_use",
                usage = UsageDto(inputTokens = 10, outputTokens = 50),
            )
        }
    }
}
