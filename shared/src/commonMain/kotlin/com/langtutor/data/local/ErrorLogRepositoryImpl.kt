package com.langtutor.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.langtutor.domain.model.ApiErrorLog
import com.langtutor.domain.repository.ErrorLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class ErrorLogRepositoryImpl(private val db: LangTutorDatabase) : ErrorLogRepository {

    private val queries get() = db.apiErrorLogQueries

    override fun observeAll(): Flow<List<ApiErrorLog>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.IO).map { rows ->
            rows.map { it.toDomain() }
        }

    override suspend fun log(errorType: String, message: String) = withContext(Dispatchers.IO) {
        queries.insert(
            error_type = errorType,
            message = message,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        queries.deleteAll()
    }

    private fun Api_error_log.toDomain() = ApiErrorLog(
        id = id,
        errorType = error_type,
        message = message,
        createdAt = created_at,
    )
}
