package com.langtutor.domain.model

data class MemoryEntry(
    val id: Long,
    val profileId: Long,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
