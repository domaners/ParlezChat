package com.langtutor.domain.usecase

import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.MessageDto
import com.langtutor.domain.model.ChatMessage
import com.langtutor.domain.model.DeliveryStatus
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.Role
import com.langtutor.domain.prompt.PromptBuilder
import com.langtutor.domain.repository.MessageRepository
import com.langtutor.domain.repository.SettingsRepository

class GenerateEngagementMessage(
    private val messageRepository: MessageRepository,
    private val claudeClient: ClaudeClient,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val DEFAULT_MODEL = "claude-sonnet-4-6"
        private const val MAX_TOKENS = 400
        private const val HISTORY_WINDOW = 10L
    }

    suspend operator fun invoke(profile: LearnerProfile): Result<ChatMessage> {
        val history = messageRepository.getHistoryWindow(profile.id, HISTORY_WINDOW)
        val model = settingsRepository.get("model")?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

        val historyDtos = history.map { MessageDto(it.role.name.lowercase(), it.content) }

        // Ensure the first message is a user turn (API requirement)
        val withKickOff = if (historyDtos.firstOrNull()?.role == "assistant") {
            listOf(MessageDto("user", PromptBuilder.kickOffUserInstruction())) + historyDtos
        } else {
            historyDtos
        }

        // Append the hidden re-engagement trigger — never persisted
        val apiMessages = withKickOff + MessageDto("user", PromptBuilder.reEngagementInstruction())

        val request = ClaudeRequest(
            model = model,
            maxTokens = MAX_TOKENS,
            system = PromptBuilder.chatSystemPrompt(profile),
            messages = apiMessages,
        )
        return claudeClient.send(request).map { response ->
            val text = response.content.firstOrNull { it.type == "text" }?.text.orEmpty()
            messageRepository.insert(profile.id, Role.ASSISTANT, text, DeliveryStatus.SENT)
        }
    }
}
