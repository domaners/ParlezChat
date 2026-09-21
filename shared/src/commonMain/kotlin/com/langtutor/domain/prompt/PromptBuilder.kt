package com.langtutor.domain.prompt

import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.model.displayName

/**
 * Renders system prompts and the kick-off instruction for every call type.
 * All prompt text lives here so tuning is easy without touching use cases.
 */
object PromptBuilder {

    fun chatSystemPrompt(profile: LearnerProfile): String {
        val level = profile.level.displayName()
        val duration = profile.studyDuration.displayName()
        val interests = if (profile.interests.isEmpty()) "general topics" else profile.interests.joinToString(", ")
        return """
            You are a friendly conversation partner helping the user practise ${profile.targetLanguage}. The user's native language is ${profile.nativeLanguage}.
            Their level is $level (studied for $duration). Their interests include: $interests.

            Rules:
            - Reply ONLY in ${profile.targetLanguage}, even if the user writes in ${profile.nativeLanguage}. Never include translations or explanations unless the user's message is impossible to answer otherwise.
            - Keep vocabulary and grammar suited to $level. Use short messages (1–3 sentences) like a text-message chat.
            - Steer the conversation toward the user's interests. Ask one simple follow-up question at a time.
            - Do not correct mistakes unless asked. Never break character.
        """.trimIndent()
    }

    fun explanationSystemPrompt(profile: LearnerProfile): String =
        "Explain the given ${profile.targetLanguage} message in ${profile.nativeLanguage}. " +
        "Grade vocabulary and grammar explanations to the user's ${profile.level.displayName()} level. " +
        "Be concise: a translation plus up to five short grammar or vocabulary notes."

    fun vocabularySystemPrompt(profile: LearnerProfile): String =
        "Define the given ${profile.targetLanguage} word for a ${profile.level.displayName()} learner. " +
        "Write the definition and example sentence translation in ${profile.nativeLanguage}. " +
        "The example sentence must be in ${profile.targetLanguage}."

    /** Hidden first user turn used only to kick off a conversation; never persisted. */
    fun kickOffUserInstruction(): String = "Begin the conversation now."
}
