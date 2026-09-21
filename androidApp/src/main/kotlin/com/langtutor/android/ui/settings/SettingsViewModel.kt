package com.langtutor.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.security.ApiKeyStore
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.ProficiencyLevel
import com.langtutor.domain.model.StudyDuration
import com.langtutor.domain.repository.ProfileRepository
import com.langtutor.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profile: LearnerProfile? = null,
    val apiKeyMasked: String = "",
    val hasApiKey: Boolean = false,
    val modelOverride: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

class SettingsViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val claudeClient: ClaudeClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeActive().collectLatest { profile ->
                val key = apiKeyStore.get()
                val model = settingsRepository.get("model") ?: ""
                _uiState.update {
                    it.copy(
                        profile = profile,
                        hasApiKey = key != null,
                        apiKeyMasked = key?.maskKey() ?: "",
                        modelOverride = model,
                    )
                }
            }
        }
    }

    fun updateProfile(
        nativeLanguage: String,
        interests: List<String>,
        level: ProficiencyLevel,
        studyDuration: StudyDuration,
    ) {
        val id = _uiState.value.profile?.id ?: return
        viewModelScope.launch {
            runCatching { profileRepository.update(id, nativeLanguage, interests, level, studyDuration) }.fold(
                onSuccess = { _uiState.update { it.copy(successMessage = "Settings saved.") } },
                onFailure = { _uiState.update { it.copy(error = "Something went wrong.") } },
            )
        }
    }

    fun replaceApiKey(newKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            claudeClient.validateKey(newKey).fold(
                onSuccess = {
                    apiKeyStore.store(newKey)
                    _uiState.update { it.copy(isLoading = false, hasApiKey = true, apiKeyMasked = newKey.maskKey(), successMessage = "API key updated.") }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false, error = "That key wasn't accepted.") }
                },
            )
        }
    }

    fun removeApiKey(onKeyRemoved: () -> Unit) {
        viewModelScope.launch {
            apiKeyStore.delete()
            settingsRepository.delete("onboarding_complete")
            _uiState.update { it.copy(hasApiKey = false, apiKeyMasked = "") }
            onKeyRemoved()
        }
    }

    fun setModelOverride(model: String) {
        viewModelScope.launch {
            if (model.isBlank()) settingsRepository.delete("model") else settingsRepository.set("model", model)
            _uiState.update { it.copy(modelOverride = model) }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }

    private fun String.maskKey(): String {
        if (length <= 8) return "••••••••"
        return "${take(7)}…${takeLast(4)}"
    }
}
