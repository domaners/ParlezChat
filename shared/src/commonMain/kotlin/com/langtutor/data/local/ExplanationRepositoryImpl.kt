package com.langtutor.data.local

import com.langtutor.domain.model.Explanation
import com.langtutor.domain.model.GrammarNote
import com.langtutor.domain.repository.ExplanationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.langtutor.data.local.Explanation as DbExplanation

class ExplanationRepositoryImpl(private val db: LangTutorDatabase) : ExplanationRepository {

    private val queries get() = db.explanationQueries

    override suspend fun getByMessageId(messageId: Long): Explanation? = withContext(Dispatchers.IO) {
        queries.selectByMessageId(messageId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun insert(explanation: Explanation) = withContext(Dispatchers.IO) {
        queries.insert(
            message_id = explanation.messageId,
            translation = explanation.translation,
            grammar_notes = Json.encodeToString(explanation.grammarNotes),
            created_at = explanation.createdAt,
        )
    }

    private fun DbExplanation.toDomain() = Explanation(
        messageId = message_id,
        translation = translation,
        grammarNotes = Json.decodeFromString(grammar_notes),
        createdAt = created_at,
    )
}
