package com.langtutor.data.local

import com.langtutor.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(private val db: LangTutorDatabase) : SettingsRepository {

    private val queries get() = db.appSettingQueries

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        queries.select(key).executeAsOneOrNull()
    }

    override suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        queries.upsert(key, value)
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        queries.delete(key)
    }
}
