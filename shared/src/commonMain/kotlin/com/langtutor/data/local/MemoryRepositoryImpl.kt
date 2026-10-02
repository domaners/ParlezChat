package com.langtutor.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.langtutor.domain.model.MemoryEntry
import com.langtutor.domain.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class MemoryRepositoryImpl(private val db: LangTutorDatabase) : MemoryRepository {

    private val queries get() = db.memoryEntryQueries

    override fun observeByProfile(profileId: Long): Flow<List<MemoryEntry>> =
        queries.selectByProfile(profileId).asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getByProfile(profileId: Long): List<MemoryEntry> = withContext(Dispatchers.IO) {
        queries.selectByProfile(profileId).executeAsList().map { it.toDomain() }
    }

    override suspend fun add(profileId: Long, content: String): MemoryEntry = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = db.transactionWithResult {
            queries.insert(profile_id = profileId, content = content, created_at = now, updated_at = now)
            queries.lastInsertRowId().executeAsOne()
        }
        MemoryEntry(id = id, profileId = profileId, content = content, createdAt = now, updatedAt = now)
    }

    override suspend fun update(id: Long, content: String) = withContext(Dispatchers.IO) {
        queries.updateContent(content = content, updated_at = Clock.System.now().toEpochMilliseconds(), id = id)
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        queries.delete(id)
    }

    private fun Memory_entry.toDomain() = MemoryEntry(
        id = id,
        profileId = profile_id,
        content = content,
        createdAt = created_at,
        updatedAt = updated_at,
    )
}
