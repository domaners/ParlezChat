package com.langtutor.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlockDto>,
    @SerialName("stop_reason") val stopReason: String?,
    val usage: UsageDto,
)

/** Flat DTO handles both text and tool_use blocks via nullable fields. */
@Serializable
data class ContentBlockDto(
    val type: String,
    // text block
    val text: String? = null,
    // tool_use block
    val id: String? = null,
    val name: String? = null,
    val input: JsonElement? = null,
)

@Serializable
data class UsageDto(
    @SerialName("input_tokens") val inputTokens: Int,
    @SerialName("output_tokens") val outputTokens: Int,
)
