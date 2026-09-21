package com.langtutor.domain.repository

import com.langtutor.domain.model.ChatMessage
import com.langtutor.domain.model.DeliveryStatus
import com.langtutor.domain.model.Role
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeByProfile(profileId: Long): Flow<List<ChatMessage>>
    suspend fun getHistoryWindow(profileId: Long, limit: Long): List<ChatMessage>
    suspend fun insert(profileId: Long, role: Role, content: String, status: DeliveryStatus): ChatMessage
    suspend fun updateStatus(id: Long, status: DeliveryStatus)
    suspend fun getById(id: Long): ChatMessage?
}
