package com.langtutor.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.langtutor.domain.model.ChatMessage
import com.langtutor.domain.model.DeliveryStatus
import com.langtutor.domain.model.Role
import com.langtutor.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class MessageRepositoryImpl(private val db: LangTutorDatabase) : MessageRepository {

    private val queries get() = db.messageQueries

    override fun observeByProfile(profileId: Long): Flow<List<ChatMessage>> =
        queries.selectByProfile(profileId).asFlow().mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getHistoryWindow(profileId: Long, limit: Long): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            // Query returns newest-first (DESC); reverse to get chronological order for the API.
            queries.selectHistoryWindow(profileId, limit).executeAsList().reversed().map { it.toDomain() }
        }

    override suspend fun insert(
        profileId: Long,
        role: Role,
        content: String,
        status: DeliveryStatus,
    ): ChatMessage = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = db.transactionWithResult {
            queries.insert(
                profile_id = profileId,
                role = role.name,
                content = content,
                status = status.name,
                created_at = now,
            )
            queries.lastInsertRowId().executeAsOne()
        }
        queries.selectById(id).executeAsOne().toDomain()
    }

    override suspend fun updateStatus(id: Long, status: DeliveryStatus) = withContext(Dispatchers.IO) {
        queries.updateStatus(status = status.name, id = id)
    }

    override suspend fun getById(id: Long): ChatMessage? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    private fun Message.toDomain() = ChatMessage(
        id = id,
        profileId = profile_id,
        role = Role.valueOf(role),
        content = content,
        status = DeliveryStatus.valueOf(status),
        createdAt = created_at,
    )
}
