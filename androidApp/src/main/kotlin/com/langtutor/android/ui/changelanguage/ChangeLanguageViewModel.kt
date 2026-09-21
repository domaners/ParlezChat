package com.langtutor.android.ui.changelanguage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChangeLanguageUiState(
    val profiles: List<LearnerProfile> = emptyList(),
    val activeProfileId: Long? = null,
    val nativeLanguage: String = "",
    val isAddingLanguage: Boolean = false,
    // New-language sub-flow state
    val newTargetLanguage: String = "",
    val newInterests: List<String> = emptyList(),
    val newLevel: ProficiencyLevel? = null,
    val newStudyDuration: StudyDuration? = null,
    val addStep: Int = 1,   // 1=target lang, 2=interests, 3=level, 4=duration
    val isLoading: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
)

class ChangeLanguageViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeLanguageUiState())
    val uiState: StateFlow<ChangeLanguageUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeAll().collectLatest { profiles ->
                val active = profiles.firstOrNull { it.isActive }
                _uiState.update {
                    it.copy(
                        profiles = profiles,
                        activeProfileId = active?.id,
                        nativeLanguage = active?.nativeLanguage ?: it.nativeLanguage,
                    )
                }
            }
        }
    }

    fun selectProfile(id: Long) {
        viewModelScope.launch {
            profileRepository.setActive(id)
            _uiState.update { it.copy(isComplete = true) }
        }
    }

    fun startAddLanguage() {
        _uiState.update { it.copy(isAddingLanguage = true, addStep = 1, error = null) }
    }

    fun setTargetLanguage(language: String) {
        if (language == _uiState.value.nativeLanguage) {
            _uiState.update { it.copy(error = "Target language must differ from your native language.") }
            return
        }
        _uiState.update { it.copy(newTargetLanguage = language, addStep = 2, error = null) }
    }

    fun setInterests(interests: List<String>) {
        _uiState.update { it.copy(newInterests = interests, addStep = 3) }
    }

    fun setLevel(level: ProficiencyLevel) {
        _uiState.update { it.copy(newLevel = level, addStep = 4) }
    }

    fun setStudyDuration(duration: StudyDuration) {
        _uiState.update { it.copy(newStudyDuration = duration) }
        createProfile()
    }

    private fun createProfile() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                profileRepository.create(
                    nativeLanguage = s.nativeLanguage,
                    targetLanguage = s.newTargetLanguage,
                    interests = s.newInterests,
                    level = s.newLevel!!,
                    studyDuration = s.newStudyDuration!!,
                )
            }.fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, isComplete = true) } },
                onFailure = { _uiState.update { it.copy(isLoading = false, error = "Something went wrong.") } },
            )
        }
    }
}
