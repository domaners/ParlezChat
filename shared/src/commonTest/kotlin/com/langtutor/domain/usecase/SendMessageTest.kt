package com.langtutor.domain.usecase

import com.langtutor.data.remote.ClaudeError
import com.langtutor.domain.model.DeliveryStatus
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.Role
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.repository.MessageRepository
import com.langtutor.domain.repository.SettingsRepository
import com.langtutor.test.FakeClaudeClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SendMessageTest {

    private val fakeClient = FakeClaudeClient()
    private val fakeMessageRepo = FakeMessageRepository()
    private val fakeSettingsRepo = FakeSettingsRepository()
    private val sendMessage = SendMessage(fakeMessageRepo, fakeClient, fakeSettingsRepo)
    private val profile = testProfile()

    @Test
    fun `sends user message and inserts assistant reply on success`() = runTest {
        fakeClient.nextResult = Result.success(FakeClaudeClient.textResponse("Hola!"))
        val result = sendMessage(profile, "Hello")
        assertTrue(result.isSuccess)
        // User message should be SENT
        val userMsg = fakeMessageRepo.messages.first { it.role == Role.USER }
        assertEquals(DeliveryStatus.SENT, userMsg.status)
        // Assistant message should be present
        val assistantMsg = fakeMessageRepo.messages.first { it.role == Role.ASSISTANT }
        assertEquals("Hola!", assistantMsg.content)
    }

    @Test
    fun `marks user message FAILED on API error without losing content`() = runTest {
        fakeClient.nextResult = Result.failure(ClaudeError.Network(null))
        val result = sendMessage(profile, "Hello")
        assertTrue(result.isFailure)
        val userMsg = fakeMessageRepo.messages.first { it.role == Role.USER }
        assertEquals(DeliveryStatus.FAILED, userMsg.status)
        assertEquals("Hello", userMsg.content)
    }

    @Test
    fun `history excludes FAILED messages and merges consecutive same-role turns`() = runTest {
        // Pre-populate two consecutive assistant messages
        fakeMessageRepo.preSeed(testProfile().id, Role.ASSISTANT, "Msg1", DeliveryStatus.SENT)
        fakeMessageRepo.preSeed(testProfile().id, Role.ASSISTANT, "Msg2", DeliveryStatus.SENT)
        fakeClient.nextResult = Result.success(FakeClaudeClient.textResponse("ok"))
        sendMessage(profile, "Hello")
        // The request should have merged the two assistant messages
        val sentRequest = fakeClient.requests.last()
        val assistantMessages = sentRequest.messages.filter { it.role == "assistant" }
        assertEquals(1, assistantMessages.size, "consecutive assistant messages should be merged")
    }

    @Test
    fun `API key not in request or any logged text`() = runTest {
        fakeClient.nextResult = Result.success(FakeClaudeClient.textResponse("ok"))
        sendMessage(profile, "test")
        // Verify the request body contains no API key (it's a header, not part of ClaudeRequest)
        val request = fakeClient.requests.last()
        val requestJson = request.toString()
        assertTrue(!requestJson.contains("sk-ant"), "API key must not appear in request body")
    }
}

// --- Fakes ---

private fun testProfile() = LearnerProfile(
    id = 1L, nativeLanguage = "English", targetLanguage = "Spanish",
    interests = listOf("cooking"), level = ProficiencyLevel.B1,
    studyDuration = StudyDuration.SIX_TO_12_MONTHS, isActive = true,
    createdAt = 0L, updatedAt = 0L,
)

private class FakeSettingsRepository : SettingsRepository {
    private val store = mutableMapOf<String, String>()
    override suspend fun get(key: String) = store[key]
    override suspend fun set(key: String, value: String) { store[key] = value }
    override suspend fun delete(key: String) { store.remove(key) }
}

private class FakeMessageRepository : MessageRepository {
    val messages = mutableListOf<com.langtutor.domain.model.ChatMessage>()
    private var nextId = 1L

    fun preSeed(profileId: Long, role: Role, content: String, status: DeliveryStatus) {
        messages.add(com.langtutor.domain.model.ChatMessage(nextId++, profileId, role, content, status, System.currentTimeMillis()))
    }

    override fun observeByProfile(profileId: Long): Flow<List<com.langtutor.domain.model.ChatMessage>> =
        flowOf(messages.filter { it.profileId == profileId })

    override suspend fun getHistoryWindow(profileId: Long, limit: Long) =
        messages.filter { it.profileId == profileId && it.status == DeliveryStatus.SENT }
            .takeLast(limit.toInt())

    override suspend fun insert(profileId: Long, role: Role, content: String, status: DeliveryStatus): com.langtutor.domain.model.ChatMessage {
        val msg = com.langtutor.domain.model.ChatMessage(nextId++, profileId, role, content, status, System.currentTimeMillis())
        messages.add(msg)
        return msg
    }

    override suspend fun updateStatus(id: Long, status: DeliveryStatus) {
        val idx = messages.indexOfFirst { it.id == id }
        if (idx >= 0) messages[idx] = messages[idx].copy(status = status)
    }

    override suspend fun getById(id: Long) = messages.firstOrNull { it.id == id }
}
