package com.langtutor.android.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.VocabEntry
import com.langtutor.domain.repository.ProfileRepository
import com.langtutor.domain.repository.VocabRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DictionaryUiState(
    val profile: LearnerProfile? = null,
    val entries: List<VocabEntry> = emptyList(),
    val query: String = "",
    val isSearching: Boolean = false,
)

class DictionaryViewModel(
    private val profileRepository: ProfileRepository,
    private val vocabRepository: VocabRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeActive().collectLatest { profile ->
                _uiState.update { it.copy(profile = profile) }
                if (profile != null) {
                    vocabRepository.observeByProfile(profile.id).collectLatest { entries ->
                        if (_uiState.value.query.isBlank()) {
                            _uiState.update { it.copy(entries = entries) }
                        }
                    }
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            if (query.isBlank()) {
                vocabRepository.observeByProfile(profile.id).first().let { entries ->
                    _uiState.update { it.copy(entries = entries) }
                }
            } else {
                val results = vocabRepository.search(profile.id, "%$query%")
                _uiState.update { it.copy(entries = results) }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { vocabRepository.delete(id) }
    }
}
