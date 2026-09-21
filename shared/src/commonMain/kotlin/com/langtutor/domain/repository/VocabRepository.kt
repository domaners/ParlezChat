package com.langtutor.domain.repository

import com.langtutor.domain.model.VocabEntry
import kotlinx.coroutines.flow.Flow

interface VocabRepository {
    fun observeByProfile(profileId: Long): Flow<List<VocabEntry>>
    suspend fun findByTerm(profileId: Long, term: String): VocabEntry?
    suspend fun insert(
        profileId: Long,
        term: String,
        partOfSpeech: String?,
        definition: String,
        exampleTarget: String,
        exampleNative: String,
        sourceMessageId: Long?,
    ): VocabEntry
    suspend fun delete(id: Long)
    suspend fun search(profileId: Long, query: String): List<VocabEntry>
}
