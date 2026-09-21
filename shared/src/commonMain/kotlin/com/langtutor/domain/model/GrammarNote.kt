package com.langtutor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GrammarNote(val theme: String, val note: String)  // note is in the native language
