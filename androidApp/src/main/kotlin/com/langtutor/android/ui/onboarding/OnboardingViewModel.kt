package com.langtutor.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.security.ApiKeyStore
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.repository.ProfileRepository
import com.langtutor.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Int = 1,  // 1=API key, 2=native lang, 3=target lang, 4=interests, 5=level, 6=duration
    val apiKey: String = "",
    val nativeLanguage: String = "",
    val targetLanguage: String = "",
    val interests: List<String> = emptyList(),
    val level: ProficiencyLevel? = null,
    val studyDuration: StudyDuration? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
)

class OnboardingViewModel(
    private val apiKeyStore: ApiKeyStore,
    private val claudeClient: ClaudeClient,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun validateApiKey(key: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            claudeClient.validateKey(key).fold(
                onSuccess = {
                    apiKeyStore.store(key)
                    _uiState.update { it.copy(apiKey = key, isLoading = false, step = 2) }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false, error = "That key wasn't accepted. Please check and try again.") }
                },
            )
        }
    }

    fun setNativeLanguage(language: String) {
        _uiState.update { it.copy(nativeLanguage = language, step = 3, error = null) }
    }

    fun setTargetLanguage(language: String) {
        if (language == _uiState.value.nativeLanguage) {
            _uiState.update { it.copy(error = "Target language must differ from your native language.") }
            return
        }
        _uiState.update { it.copy(targetLanguage = language, step = 4, error = null) }
    }

    fun setInterests(interests: List<String>) {
        _uiState.update { it.copy(interests = interests.take(10), step = 5, error = null) }
    }

    fun setLevel(level: ProficiencyLevel) {
        _uiState.update { it.copy(level = level, step = 6, error = null) }
    }

    fun setStudyDuration(duration: StudyDuration) {
        _uiState.update { it.copy(studyDuration = duration) }
        completeOnboarding()
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                profileRepository.create(
                    nativeLanguage = state.nativeLanguage,
                    targetLanguage = state.targetLanguage,
                    interests = state.interests,
                    level = state.level!!,
                    studyDuration = state.studyDuration!!,
                )
                settingsRepository.set("onboarding_complete", "true")
            }.fold(
                onSuccess = { _uiState.update { it.copy(isLoading = false, isComplete = true) } },
                onFailure = { _uiState.update { it.copy(isLoading = false, error = "Something went wrong. Please try again.") } },
            )
        }
    }
}
