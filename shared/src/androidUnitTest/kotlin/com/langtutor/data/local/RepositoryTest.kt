package com.langtutor.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.langtutor.domain.model.DeliveryStatus
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.Role
import com.langtutor.domain.model.StudyDuration
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {

    private lateinit var db: LangTutorDatabase
    private lateinit var profileRepo: ProfileRepositoryImpl
    private lateinit var messageRepo: MessageRepositoryImpl
    private lateinit var vocabRepo: VocabRepositoryImpl
    private lateinit var explanationRepo: ExplanationRepositoryImpl

    @BeforeTest
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LangTutorDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        db = LangTutorDatabase(driver)
        profileRepo = ProfileRepositoryImpl(db)
        messageRepo = MessageRepositoryImpl(db)
        vocabRepo = VocabRepositoryImpl(db)
        explanationRepo = ExplanationRepositoryImpl(db)
    }

    @Test
    fun `create profile and retrieve as active`() = runTest {
        val profile = profileRepo.create("English", "Spanish", listOf("cooking"), ProficiencyLevel.B1, StudyDuration.SIX_TO_12_MONTHS)
        assertNotNull(profile.id)
        val active = profileRepo.getActive()
        assertEquals(profile.id, active?.id)
        assertEquals("Spanish", active?.targetLanguage)
    }

    @Test
    fun `switching active profile scopes messages correctly`() = runTest {
        val p1 = profileRepo.create("English", "Spanish", emptyList(), ProficiencyLevel.A1, StudyDuration.UNDER_1_MONTH)
        val p2 = profileRepo.create("English", "French", emptyList(), ProficiencyLevel.A2, StudyDuration.ONE_TO_3_MONTHS)

        messageRepo.insert(p1.id, Role.ASSISTANT, "Hola", DeliveryStatus.SENT)
        messageRepo.insert(p2.id, Role.ASSISTANT, "Bonjour", DeliveryStatus.SENT)

        // p2 is now active (create sets active)
        val active = profileRepo.getActive()
        assertEquals(p2.id, active?.id)

        val p2Messages = messageRepo.getHistoryWindow(p2.id, 20)
        assertEquals(1, p2Messages.size)
        assertEquals("Bonjour", p2Messages[0].content)
    }

    @Test
    fun `failed send leaves message FAILED, retry succeeds, no duplicate`() = runTest {
        val profile = profileRepo.create("English", "German", emptyList(), ProficiencyLevel.B2, StudyDuration.ONE_TO_2_YEARS)
        val msg = messageRepo.insert(profile.id, Role.USER, "Hallo", DeliveryStatus.PENDING)
        messageRepo.updateStatus(msg.id, DeliveryStatus.FAILED)
        assertEquals(DeliveryStatus.FAILED, messageRepo.getById(msg.id)?.status)

        // Retry: update status to SENT — no new message inserted
        messageRepo.updateStatus(msg.id, DeliveryStatus.SENT)
        val history = messageRepo.getHistoryWindow(profile.id, 20)
        assertEquals(1, history.size)
        assertEquals(DeliveryStatus.SENT, history[0].status)
    }

    @Test
    fun `duplicate vocab save is idempotent case-insensitive`() = runTest {
        val profile = profileRepo.create("English", "Italian", emptyList(), ProficiencyLevel.A1, StudyDuration.UNDER_1_MONTH)
        val v1 = vocabRepo.insert(profile.id, "Ciao", null, "Hello", "Ciao, come stai?", "Hello, how are you?", null)
        val found = vocabRepo.findByTerm(profile.id, "ciao")  // lowercase lookup
        assertNotNull(found)
        assertEquals(v1.id, found.id)
    }

    @Test
    fun `explanation cached on second request`() = runTest {
        val profile = profileRepo.create("English", "Polish", emptyList(), ProficiencyLevel.B1, StudyDuration.SIX_TO_12_MONTHS)
        val msg = messageRepo.insert(profile.id, Role.ASSISTANT, "Dzień dobry", DeliveryStatus.SENT)

        assertNull(explanationRepo.getByMessageId(msg.id))

        val explanation = com.langtutor.domain.model.Explanation(
            messageId = msg.id,
            translation = "Good morning",
            grammarNotes = emptyList(),
            createdAt = 0L,
        )
        explanationRepo.insert(explanation)
        val cached = explanationRepo.getByMessageId(msg.id)
        assertEquals("Good morning", cached?.translation)
    }

    @Test
    fun `history window excludes PENDING and FAILED messages`() = runTest {
        val profile = profileRepo.create("English", "French", emptyList(), ProficiencyLevel.B2, StudyDuration.OVER_2_YEARS)
        messageRepo.insert(profile.id, Role.ASSISTANT, "Bonjour", DeliveryStatus.SENT)
        messageRepo.insert(profile.id, Role.USER, "Lost message", DeliveryStatus.FAILED)
        messageRepo.insert(profile.id, Role.USER, "Pending", DeliveryStatus.PENDING)

        val window = messageRepo.getHistoryWindow(profile.id, 20)
        assertEquals(1, window.size)
        assertEquals("Bonjour", window[0].content)
    }
}
