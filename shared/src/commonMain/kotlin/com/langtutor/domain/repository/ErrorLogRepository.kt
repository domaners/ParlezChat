package com.langtutor.domain.repository

import com.langtutor.domain.model.ApiErrorLog
import kotlinx.coroutines.flow.Flow

interface ErrorLogRepository {
    fun observeAll(): Flow<List<ApiErrorLog>>
    suspend fun log(errorType: String, message: String)
    suspend fun clearAll()
}
