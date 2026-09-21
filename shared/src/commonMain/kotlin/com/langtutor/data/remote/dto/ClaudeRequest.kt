package com.langtutor.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<MessageDto>,
    val tools: List<ToolDto>? = null,
    @SerialName("tool_choice") val toolChoice: ToolChoiceDto? = null,
)

@Serializable
data class MessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ToolDto(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonElement,
)

@Serializable
data class ToolChoiceDto(
    val type: String,
    val name: String? = null,
)
