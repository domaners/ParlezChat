package com.langtutor.domain.usecase

import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.ClaudeResponse
import com.langtutor.data.remote.dto.MessageDto
import com.langtutor.data.remote.dto.ToolChoiceDto
import com.langtutor.domain.model.ChatMessage
import com.langtutor.domain.model.DeliveryStatus
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.Role
import com.langtutor.domain.prompt.PromptBuilder
import com.langtutor.domain.repository.MemoryRepository
import com.langtutor.domain.repository.MessageRepository
import com.langtutor.domain.repository.SettingsRepository
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SendMessage(
    private val messageRepository: MessageRepository,
    private val claudeClient: ClaudeClient,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: MemoryRepository,
) {
    companion object {
        private const val DEFAULT_MODEL = "claude-sonnet-4-6"
        private const val MAX_TOKENS_CHAT = 500
        private const val HISTORY_WINDOW = 20L
        private const val MEMORY_PROMPT_LIMIT = 40
    }

    suspend operator fun invoke(profile: LearnerProfile, text: String): Result<ChatMessage> {
        // 1. Persist user message immediately as PENDING so the UI can show it right away
        val userMsg = try {
            messageRepository.insert(profile.id, Role.USER, text, DeliveryStatus.PENDING)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return try {
            // 2. Fetch the last HISTORY_WINDOW sent messages + the new pending one
            val history = messageRepository.getHistoryWindow(profile.id, HISTORY_WINDOW).toMutableList()
            if (history.none { it.id == userMsg.id }) history.add(userMsg)

            // 3. Merge consecutive same-role messages so roles always alternate (API requirement)
            val merged = mergeConsecutiveRoles(history).map { msg ->
                MessageDto(role = msg.role.name.lowercase(), content = msg.content)
            }

            // 4. The API requires the first message to be "user". The kick-off ASSISTANT
            //    message has no persisted user turn before it, so if the history starts with
            //    an assistant turn we prepend the hidden kick-off instruction to produce a
            //    structurally valid conversation for the API.
            val apiMessages = if (merged.firstOrNull()?.role == "assistant") {
                listOf(MessageDto("user", PromptBuilder.kickOffUserInstruction())) + merged
            } else {
                merged
            }

            val model = settingsRepository.get("model")?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
            val memoryNotes = memoryRepository.getByProfile(profile.id).map { it.content }.takeLast(MEMORY_PROMPT_LIMIT)
            val request = ClaudeRequest(
                model = model,
                maxTokens = MAX_TOKENS_CHAT,
                system = PromptBuilder.chatSystemPrompt(profile, memoryNotes, memoryToolAvailable = true),
                messages = apiMessages,
                tools = listOf(PromptBuilder.memoryTool()),
                toolChoice = ToolChoiceDto(type = "auto"),
            )

            claudeClient.send(request).fold(
                onSuccess = { response ->
                    saveMemoryNotes(response, profile.id)
                    var replyText = response.content.filter { it.type == "text" }
                        .joinToString("\n") { it.text.orEmpty() }
                    if (replyText.isBlank()) {
                        // Model only emitted a tool_use block — retry once without tools to force a text reply.
                        replyText = claudeClient.send(request.copy(tools = null, toolChoice = null))
                            .getOrNull()?.content?.firstOrNull { it.type == "text" }?.text.orEmpty()
                    }
                    runCatching { messageRepository.updateStatus(userMsg.id, DeliveryStatus.SENT) }
                    val assistantMsg = messageRepository.insert(profile.id, Role.ASSISTANT, replyText, DeliveryStatus.SENT)
                    Result.success(assistantMsg)
                },
                onFailure = { error ->
                    runCatching { messageRepository.updateStatus(userMsg.id, DeliveryStatus.FAILED) }
                    Result.failure(error)
                },
            )
        } catch (e: Exception) {
            runCatching { messageRepository.updateStatus(userMsg.id, DeliveryStatus.FAILED) }
            Result.failure(e)
        }
    }

    private suspend fun saveMemoryNotes(response: ClaudeResponse, profileId: Long) {
        val toolUse = response.content.firstOrNull { it.type == "tool_use" && it.name == PromptBuilder.MEMORY_TOOL_NAME }
            ?: return
        val notes = runCatching {
            toolUse.input?.jsonObject?.get("notes")?.jsonArray?.map { it.jsonPrimitive.content }
        }.getOrNull().orEmpty()
        notes.filter { it.isNotBlank() }.forEach { note ->
            runCatching { memoryRepository.add(profileId, note.trim().take(300)) }
        }
    }

    private fun mergeConsecutiveRoles(messages: List<ChatMessage>): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        for (msg in messages) {
            val last = result.lastOrNull()
            if (last != null && last.role == msg.role) {
                result[result.lastIndex] = last.copy(content = "${last.content}\n${msg.content}")
            } else {
                result.add(msg)
            }
        }
        return result
    }
}
