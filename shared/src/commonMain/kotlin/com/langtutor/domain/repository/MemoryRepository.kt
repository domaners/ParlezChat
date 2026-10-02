package com.langtutor.domain.repository

import com.langtutor.domain.model.MemoryEntry
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun observeByProfile(profileId: Long): Flow<List<MemoryEntry>>
    suspend fun getByProfile(profileId: Long): List<MemoryEntry>
    suspend fun add(profileId: Long, content: String): MemoryEntry
    suspend fun update(id: Long, content: String)
    suspend fun delete(id: Long)
}
