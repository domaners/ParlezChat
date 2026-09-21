package com.langtutor.domain.model

data class VocabEntry(
    val id: Long,
    val profileId: Long,
    val term: String,                   // as it appears in the target language
    val partOfSpeech: String?,
    val definition: String,             // native language
    val exampleTarget: String,
    val exampleNative: String,
    val sourceMessageId: Long?,         // message it was saved from, if still present
    val createdAt: Long,
)
