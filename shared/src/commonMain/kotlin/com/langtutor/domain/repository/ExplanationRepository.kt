package com.langtutor.domain.repository

import com.langtutor.domain.model.Explanation

interface ExplanationRepository {
    suspend fun getByMessageId(messageId: Long): Explanation?
    suspend fun insert(explanation: Explanation)
}
