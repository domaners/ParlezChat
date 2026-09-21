package com.langtutor.domain.prompt

import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import kotlin.test.Test
import kotlin.test.assertContains

class PromptBuilderTest {

    private val profile = LearnerProfile(
        id = 1L,
        nativeLanguage = "English",
        targetLanguage = "Polish",
        interests = listOf("hiking", "cooking"),
        level = ProficiencyLevel.B1,
        studyDuration = StudyDuration.SIX_TO_12_MONTHS,
        isActive = true,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `chat system prompt contains target language`() {
        val prompt = PromptBuilder.chatSystemPrompt(profile)
        assertContains(prompt, "Polish")
    }

    @Test
    fun `chat system prompt contains native language`() {
        val prompt = PromptBuilder.chatSystemPrompt(profile)
        assertContains(prompt, "English")
    }

    @Test
    fun `chat system prompt contains level`() {
        val prompt = PromptBuilder.chatSystemPrompt(profile)
        assertContains(prompt, "B1", ignoreCase = true)
    }

    @Test
    fun `chat system prompt contains interests`() {
        val prompt = PromptBuilder.chatSystemPrompt(profile)
        assertContains(prompt, "hiking")
        assertContains(prompt, "cooking")
    }

    @Test
    fun `explanation system prompt contains native language`() {
        val prompt = PromptBuilder.explanationSystemPrompt(profile)
        assertContains(prompt, "English")
        assertContains(prompt, "Polish")
    }

    @Test
    fun `kick off instruction is non-empty`() {
        val instruction = PromptBuilder.kickOffUserInstruction()
        assert(instruction.isNotBlank())
    }
}
