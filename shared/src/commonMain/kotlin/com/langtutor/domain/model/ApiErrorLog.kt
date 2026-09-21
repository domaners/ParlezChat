package com.langtutor.domain.model

data class ApiErrorLog(
    val id: Long,
    val errorType: String,
    val message: String,
    val createdAt: Long,   // epoch milliseconds UTC
)
