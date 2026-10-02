package com.langtutor.android.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.MemoryEntry
import com.langtutor.domain.repository.MemoryRepository
import com.langtutor.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryUiState(
    val profile: LearnerProfile? = null,
    val entries: List<MemoryEntry> = emptyList(),
)

class MemoryViewModel(
    private val profileRepository: ProfileRepository,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeActive().collectLatest { profile ->
                _uiState.update { it.copy(profile = profile) }
                if (profile != null) {
                    memoryRepository.observeByProfile(profile.id).collectLatest { entries ->
                        _uiState.update { it.copy(entries = entries) }
                    }
                }
            }
        }
    }

    fun add(content: String) {
        val profile = _uiState.value.profile ?: return
        if (content.isBlank()) return
        viewModelScope.launch { memoryRepository.add(profile.id, content.trim()) }
    }

    fun update(id: Long, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch { memoryRepository.update(id, content.trim()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { memoryRepository.delete(id) }
    }
}
