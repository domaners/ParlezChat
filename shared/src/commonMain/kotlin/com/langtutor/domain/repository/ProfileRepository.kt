package com.langtutor.domain.repository

import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeAll(): Flow<List<LearnerProfile>>
    fun observeActive(): Flow<LearnerProfile?>
    suspend fun getActive(): LearnerProfile?
    suspend fun getById(id: Long): LearnerProfile?
    suspend fun create(
        nativeLanguage: String,
        targetLanguage: String,
        interests: List<String>,
        level: ProficiencyLevel,
        studyDuration: StudyDuration,
    ): LearnerProfile
    suspend fun setActive(id: Long)
    suspend fun update(
        id: Long,
        nativeLanguage: String,
        interests: List<String>,
        level: ProficiencyLevel,
        studyDuration: StudyDuration,
    )
    suspend fun delete(id: Long)
}
