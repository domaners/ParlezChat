package com.langtutor.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.langtutor.domain.model.VocabEntry
import com.langtutor.domain.repository.VocabRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class VocabRepositoryImpl(private val db: LangTutorDatabase) : VocabRepository {

    private val queries get() = db.vocabEntryQueries

    override fun observeByProfile(profileId: Long): Flow<List<VocabEntry>> =
        queries.selectByProfile(profileId).asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun findByTerm(profileId: Long, term: String): VocabEntry? = withContext(Dispatchers.IO) {
        queries.selectByTerm(profileId, term).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun insert(
        profileId: Long,
        term: String,
        partOfSpeech: String?,
        definition: String,
        exampleTarget: String,
        exampleNative: String,
        sourceMessageId: Long?,
    ): VocabEntry = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = db.transactionWithResult {
            queries.insert(
                profile_id = profileId,
                term = term,
                part_of_speech = partOfSpeech,
                definition = definition,
                example_target = exampleTarget,
                example_native = exampleNative,
                source_message_id = sourceMessageId,
                created_at = now,
            )
            queries.lastInsertRowId().executeAsOne()
        }
        queries.selectByTerm(profileId, term).executeAsOne().toDomain()
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        queries.delete(id)
    }

    override suspend fun search(profileId: Long, query: String): List<VocabEntry> = withContext(Dispatchers.IO) {
        queries.searchByProfile(profileId, query).executeAsList().map { it.toDomain() }
    }

    private fun Vocab_entry.toDomain() = VocabEntry(
        id = id,
        profileId = profile_id,
        term = term,
        partOfSpeech = part_of_speech,
        definition = definition,
        exampleTarget = example_target,
        exampleNative = example_native,
        sourceMessageId = source_message_id,
        createdAt = created_at,
    )
}
