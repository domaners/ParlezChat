package com.langtutor.domain.model

enum class StudyDuration {
    UNDER_1_MONTH, ONE_TO_3_MONTHS, THREE_TO_6_MONTHS,
    SIX_TO_12_MONTHS, ONE_TO_2_YEARS, OVER_2_YEARS
}

fun StudyDuration.displayName(): String = when (this) {
    StudyDuration.UNDER_1_MONTH -> "less than a month"
    StudyDuration.ONE_TO_3_MONTHS -> "1–3 months"
    StudyDuration.THREE_TO_6_MONTHS -> "3–6 months"
    StudyDuration.SIX_TO_12_MONTHS -> "6–12 months"
    StudyDuration.ONE_TO_2_YEARS -> "1–2 years"
    StudyDuration.OVER_2_YEARS -> "over 2 years"
}

fun StudyDuration.labelForUi(): String = when (this) {
    StudyDuration.UNDER_1_MONTH -> "Less than 1 month"
    StudyDuration.ONE_TO_3_MONTHS -> "1–3 months"
    StudyDuration.THREE_TO_6_MONTHS -> "3–6 months"
    StudyDuration.SIX_TO_12_MONTHS -> "6–12 months"
    StudyDuration.ONE_TO_2_YEARS -> "1–2 years"
    StudyDuration.OVER_2_YEARS -> "Over 2 years"
}
