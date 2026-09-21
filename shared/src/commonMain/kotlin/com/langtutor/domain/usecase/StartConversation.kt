package com.langtutor.domain.usecase

import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.MessageDto
import com.langtutor.domain.model.DeliveryStatus
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.Role
import com.langtutor.domain.prompt.PromptBuilder
import com.langtutor.domain.repository.MessageRepository
import com.langtutor.domain.repository.SettingsRepository

class StartConversation(
    private val messageRepository: MessageRepository,
    private val claudeClient: ClaudeClient,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        // claude-sonnet-4-6: current Sonnet model as of Aug 2025; best balance of quality and speed for chat
        private const val DEFAULT_MODEL = "claude-sonnet-4-6"
        private const val MAX_TOKENS_CHAT = 400
    }

    suspend operator fun invoke(profile: LearnerProfile): Result<Unit> {
        val model = settingsRepository.get("model") ?: DEFAULT_MODEL
        val request = ClaudeRequest(
            model = model,
            maxTokens = MAX_TOKENS_CHAT,
            system = PromptBuilder.chatSystemPrompt(profile),
            // Hidden kick-off turn per spec: send as user turn but do NOT persist it
            messages = listOf(MessageDto(role = "user", content = PromptBuilder.kickOffUserInstruction())),
        )
        return claudeClient.send(request).map { response ->
            val text = response.content.firstOrNull { it.type == "text" }?.text.orEmpty()
            messageRepository.insert(profile.id, Role.ASSISTANT, text, DeliveryStatus.SENT)
            Unit
        }
    }
}
