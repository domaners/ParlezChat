package com.langtutor.android.ui.errorlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.langtutor.domain.model.ApiErrorLog
import com.langtutor.domain.repository.ErrorLogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ErrorLogViewModel(
    private val errorLogRepository: ErrorLogRepository,
) : ViewModel() {

    val entries: StateFlow<List<ApiErrorLog>> = errorLogRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearAll() {
        viewModelScope.launch { errorLogRepository.clearAll() }
    }
}
