package com.langtutor.domain.usecase

import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.remote.ClaudeError
import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.MessageDto
import com.langtutor.data.remote.dto.ToolChoiceDto
import com.langtutor.data.remote.dto.ToolDto
import com.langtutor.domain.model.Explanation
import com.langtutor.domain.model.GrammarNote
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.prompt.PromptBuilder
import com.langtutor.domain.repository.ExplanationRepository
import com.langtutor.domain.repository.SettingsRepository
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ExplainMessage(
    private val explanationRepository: ExplanationRepository,
    private val claudeClient: ClaudeClient,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val DEFAULT_MODEL = "claude-sonnet-4-6"
        private const val MAX_TOKENS_EXPLANATION = 800
        private const val TOOL_NAME = "submit_explanation"
    }

    suspend operator fun invoke(
        profile: LearnerProfile,
        messageId: Long,
        messageText: String,
    ): Result<Explanation> {
        // Return cached result — no API call if already explained
        explanationRepository.getByMessageId(messageId)?.let { return Result.success(it) }

        val model = settingsRepository.get("model") ?: DEFAULT_MODEL
        val request = ClaudeRequest(
            model = model,
            maxTokens = MAX_TOKENS_EXPLANATION,
            system = PromptBuilder.explanationSystemPrompt(profile),
            messages = listOf(MessageDto("user", messageText)),
            tools = listOf(explanationTool()),
            toolChoice = ToolChoiceDto(type = "tool", name = TOOL_NAME),
        )

        return claudeClient.send(request).fold(
            onSuccess = { response ->
                val toolUse = response.content.firstOrNull { it.type == "tool_use" && it.name == TOOL_NAME }
                    ?: return Result.failure(ClaudeError.MalformedResponse("Missing tool_use block"))
                val input = toolUse.input
                    ?: return Result.failure(ClaudeError.MalformedResponse("Empty tool input"))

                runCatching { parseExplanation(messageId, input) }.fold(
                    onSuccess = { explanation ->
                        explanationRepository.insert(explanation)
                        Result.success(explanation)
                    },
                    onFailure = { Result.failure(ClaudeError.MalformedResponse(it.message)) },
                )
            },
            onFailure = { Result.failure(it) },
        )
    }

    private fun parseExplanation(messageId: Long, input: JsonElement): Explanation {
        val obj = input.jsonObject
        val translation = obj["translation"]!!.jsonPrimitive.content
        val grammarNotes = obj["grammar_notes"]!!.jsonArray.map { note ->
            val n = note.jsonObject
            GrammarNote(
                theme = n["theme"]!!.jsonPrimitive.content,
                note = n["note"]!!.jsonPrimitive.content,
            )
        }
        return Explanation(messageId, translation, grammarNotes, Clock.System.now().toEpochMilliseconds())
    }

    private fun explanationTool() = ToolDto(
        name = TOOL_NAME,
        description = "Return a translation and short grammar breakdown of a message.",
        inputSchema = Json.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "translation": {
                  "type": "string",
                  "description": "Translation into the user's native language"
                },
                "grammar_notes": {
                  "type": "array",
                  "maxItems": 5,
                  "items": {
                    "type": "object",
                    "properties": {
                      "theme": { "type": "string", "description": "e.g. 'accusative case', 'past tense'" },
                      "note":  { "type": "string", "description": "1-2 sentences in the native language, quoting the relevant words" }
                    },
                    "required": ["theme", "note"]
                  }
                }
              },
              "required": ["translation", "grammar_notes"]
            }
            """.trimIndent()
        ),
    )
}
