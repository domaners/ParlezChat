package com.langtutor.domain.model

data class Explanation(
    val messageId: Long,        // the assistant message explained
    val translation: String,
    val grammarNotes: List<GrammarNote>,
    val createdAt: Long,
)
