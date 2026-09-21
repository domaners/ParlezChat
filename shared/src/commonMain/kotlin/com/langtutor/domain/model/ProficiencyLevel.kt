package com.langtutor.domain.model

enum class ProficiencyLevel { A1, A2, B1, B2, C1, C2 }

fun ProficiencyLevel.displayName(): String = when (this) {
    ProficiencyLevel.A1 -> "Complete beginner (A1)"
    ProficiencyLevel.A2 -> "Elementary (A2)"
    ProficiencyLevel.B1 -> "Pre-intermediate (B1)"
    ProficiencyLevel.B2 -> "Intermediate (B2)"
    ProficiencyLevel.C1 -> "Upper-intermediate (C1)"
    ProficiencyLevel.C2 -> "Advanced (C2)"
}
