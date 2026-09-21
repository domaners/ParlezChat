package com.langtutor.domain.usecase

import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.remote.ClaudeError
import com.langtutor.data.remote.dto.ClaudeRequest
import com.langtutor.data.remote.dto.MessageDto
import com.langtutor.data.remote.dto.ToolChoiceDto
import com.langtutor.data.remote.dto.ToolDto
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.VocabEntry
import com.langtutor.domain.prompt.PromptBuilder
import com.langtutor.domain.repository.SettingsRepository
import com.langtutor.domain.repository.VocabRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SaveWord(
    private val vocabRepository: VocabRepository,
    private val claudeClient: ClaudeClient,
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val DEFAULT_MODEL = "claude-sonnet-4-6"
        private const val MAX_TOKENS_VOCAB = 300
        private const val TOOL_NAME = "submit_vocab_entry"
    }

    /** Returns the saved entry. Idempotent: if [term] already exists for this profile, returns it. */
    suspend operator fun invoke(
        profile: LearnerProfile,
        term: String,
        contextMessage: String,
        sourceMessageId: Long?,
    ): Result<VocabEntry> {
        // Case-insensitive dedup as required by spec
        vocabRepository.findByTerm(profile.id, term)?.let { return Result.success(it) }

        val model = settingsRepository.get("model") ?: DEFAULT_MODEL
        val userPrompt = "Word: \"$term\"\nContext sentence: $contextMessage"
        val request = ClaudeRequest(
            model = model,
            maxTokens = MAX_TOKENS_VOCAB,
            system = PromptBuilder.vocabularySystemPrompt(profile),
            messages = listOf(MessageDto("user", userPrompt)),
            tools = listOf(vocabTool()),
            toolChoice = ToolChoiceDto(type = "tool", name = TOOL_NAME),
        )

        return claudeClient.send(request).fold(
            onSuccess = { response ->
                val toolUse = response.content.firstOrNull { it.type == "tool_use" && it.name == TOOL_NAME }
                    ?: return Result.failure(ClaudeError.MalformedResponse("Missing tool_use block"))
                val input = toolUse.input
                    ?: return Result.failure(ClaudeError.MalformedResponse("Empty tool input"))

                runCatching { parseEntry(input, profile.id, sourceMessageId) }.fold(
                    onSuccess = { entry ->
                        val saved = vocabRepository.insert(
                            profileId = entry.profileId,
                            term = entry.term,
                            partOfSpeech = entry.partOfSpeech,
                            definition = entry.definition,
                            exampleTarget = entry.exampleTarget,
                            exampleNative = entry.exampleNative,
                            sourceMessageId = entry.sourceMessageId,
                        )
                        Result.success(saved)
                    },
                    onFailure = { Result.failure(ClaudeError.MalformedResponse(it.message)) },
                )
            },
            onFailure = { Result.failure(it) },
        )
    }

    private fun parseEntry(input: JsonElement, profileId: Long, sourceMessageId: Long?): VocabEntry {
        val obj = input.jsonObject
        return VocabEntry(
            id = 0,
            profileId = profileId,
            term = obj["term"]!!.jsonPrimitive.content,
            partOfSpeech = obj["part_of_speech"]?.jsonPrimitive?.contentOrNull,
            definition = obj["definition"]!!.jsonPrimitive.content,
            exampleTarget = obj["example_target"]!!.jsonPrimitive.content,
            exampleNative = obj["example_native"]!!.jsonPrimitive.content,
            sourceMessageId = sourceMessageId,
            createdAt = 0,
        )
    }

    private fun vocabTool() = ToolDto(
        name = TOOL_NAME,
        description = "Define a word from a message in the user's target language.",
        inputSchema = Json.parseToJsonElement(
            """
            {
              "type": "object",
              "properties": {
                "term":           { "type": "string", "description": "Dictionary form of the word" },
                "part_of_speech": { "type": "string" },
                "definition":     { "type": "string", "description": "Native-language definition, one line" },
                "example_target": { "type": "string", "description": "New example sentence at the user's level in the target language" },
                "example_native": { "type": "string", "description": "Translation of the example into the native language" }
              },
              "required": ["term", "definition", "example_target", "example_native"]
            }
            """.trimIndent()
        ),
    )
}
