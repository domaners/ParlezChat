package com.langtutor.domain.model

data class LearnerProfile(
    val id: Long,
    val nativeLanguage: String,
    val targetLanguage: String,
    val interests: List<String>,    // free text, trimmed, max 10
    val level: ProficiencyLevel,
    val studyDuration: StudyDuration,
    val isActive: Boolean,          // exactly one profile is active
    val createdAt: Long,
    val updatedAt: Long,
)
