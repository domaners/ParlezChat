package com.langtutor.domain.model

data class ChatMessage(
    val id: Long,
    val profileId: Long,
    val role: Role,
    val content: String,
    val status: DeliveryStatus,
    val createdAt: Long,
)
