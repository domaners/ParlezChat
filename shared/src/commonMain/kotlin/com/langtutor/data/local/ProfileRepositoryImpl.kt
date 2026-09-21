package com.langtutor.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProfileRepositoryImpl(private val db: LangTutorDatabase) : ProfileRepository {

    private val queries get() = db.learnerProfileQueries

    override fun observeAll(): Flow<List<LearnerProfile>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    override fun observeActive(): Flow<LearnerProfile?> =
        queries.selectActive().asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toDomain() }

    override suspend fun getActive(): LearnerProfile? = withContext(Dispatchers.IO) {
        queries.selectActive().executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getById(id: Long): LearnerProfile? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun create(
        nativeLanguage: String,
        targetLanguage: String,
        interests: List<String>,
        level: ProficiencyLevel,
        studyDuration: StudyDuration,
    ): LearnerProfile = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = db.transactionWithResult {
            queries.clearAllActive()
            queries.insert(
                native_language = nativeLanguage,
                target_language = targetLanguage,
                interests = Json.encodeToString(interests),
                level = level.name,
                study_duration = studyDuration.name,
                is_active = 1L,
                created_at = now,
                updated_at = now,
            )
            queries.lastInsertRowId().executeAsOne()
        }
        queries.selectById(id).executeAsOne().toDomain()
    }

    override suspend fun setActive(id: Long) = withContext(Dispatchers.IO) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            queries.clearAllActive()
            queries.setActive(is_active = 1L, updated_at = now, id = id)
        }
    }

    override suspend fun update(
        id: Long,
        nativeLanguage: String,
        interests: List<String>,
        level: ProficiencyLevel,
        studyDuration: StudyDuration,
    ) = withContext(Dispatchers.IO) {
        queries.updateProfile(
            native_language = nativeLanguage,
            interests = Json.encodeToString(interests),
            level = level.name,
            study_duration = studyDuration.name,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = id,
        )
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        queries.delete(id)
    }

    private fun Learner_profile.toDomain() = LearnerProfile(
        id = id,
        nativeLanguage = native_language,
        targetLanguage = target_language,
        interests = Json.decodeFromString(interests),
        level = ProficiencyLevel.valueOf(level),
        studyDuration = StudyDuration.valueOf(study_duration),
        isActive = is_active == 1L,
        createdAt = created_at,
        updatedAt = updated_at,
    )
}
