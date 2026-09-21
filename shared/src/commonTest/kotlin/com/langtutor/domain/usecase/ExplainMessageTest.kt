package com.langtutor.domain.usecase

import com.langtutor.domain.model.Explanation
import com.langtutor.domain.model.GrammarNote
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.repository.ExplanationRepository
import com.langtutor.domain.repository.SettingsRepository
import com.langtutor.test.FakeClaudeClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExplainMessageTest {

    private val fakeClient = FakeClaudeClient()
    private val fakeExplanationRepo = FakeExplanationRepository()
    private val fakeSettingsRepo = FakeExplainSettingsRepository()
    private val explainMessage = ExplainMessage(fakeExplanationRepo, fakeClient, fakeSettingsRepo)
    private val profile = testProfile()

    @Test
    fun `returns translation and grammar notes on success`() = runTest {
        fakeClient.nextResult = Result.success(
            FakeClaudeClient.toolResponse(
                "submit_explanation",
                """{"translation":"Good morning","grammar_notes":[{"theme":"greeting","note":"Formal greeting used in the morning"}]}""",
            )
        )
        val result = explainMessage(profile, 1L, "Buenos días")
        assertTrue(result.isSuccess)
        val explanation = result.getOrThrow()
        assertEquals("Good morning", explanation.translation)
        assertEquals(1, explanation.grammarNotes.size)
        assertEquals("greeting", explanation.grammarNotes[0].theme)
    }

    @Test
    fun `caches explanation and makes no second API call`() = runTest {
        val cached = Explanation(1L, "Cached translation", emptyList(), 0L)
        fakeExplanationRepo.store[1L] = cached
        val result = explainMessage(profile, 1L, "Buenos días")
        assertTrue(result.isSuccess)
        assertEquals("Cached translation", result.getOrThrow().translation)
        assertEquals(0, fakeClient.callCount, "should not call the API when explanation is cached")
    }

    @Test
    fun `stores explanation after first fetch`() = runTest {
        fakeClient.nextResult = Result.success(
            FakeClaudeClient.toolResponse(
                "submit_explanation",
                """{"translation":"Good night","grammar_notes":[]}""",
            )
        )
        explainMessage(profile, 2L, "Buenas noches")
        assertTrue(fakeExplanationRepo.store.containsKey(2L))
    }
}

private fun testProfile() = LearnerProfile(
    id = 1L, nativeLanguage = "English", targetLanguage = "Spanish",
    interests = listOf("travel"), level = ProficiencyLevel.A2,
    studyDuration = StudyDuration.ONE_TO_3_MONTHS, isActive = true,
    createdAt = 0L, updatedAt = 0L,
)

private class FakeExplainSettingsRepository : SettingsRepository {
    override suspend fun get(key: String) = null
    override suspend fun set(key: String, value: String) {}
    override suspend fun delete(key: String) {}
}

private class FakeExplanationRepository : ExplanationRepository {
    val store = mutableMapOf<Long, Explanation>()
    override suspend fun getByMessageId(messageId: Long) = store[messageId]
    override suspend fun insert(explanation: Explanation) { store[explanation.messageId] = explanation }
}
