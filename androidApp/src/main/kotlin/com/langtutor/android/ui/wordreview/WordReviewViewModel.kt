package com.langtutor.android.ui.wordreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.langtutor.domain.repository.ProfileRepository
import com.langtutor.domain.repository.VocabRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordReviewUiState(
    val term: String = "",
    val options: List<String> = emptyList(),
    val correctDefinition: String = "",
    val selectedOption: String? = null,
    val answered: Boolean = false,
    val isCorrect: Boolean? = null,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val currentWordId: Long = -1L,
    val isLoading: Boolean = false,
    val tooFewWords: Boolean = false,
    val error: String? = null,
)

class WordReviewViewModel(
    private val profileRepository: ProfileRepository,
    private val vocabRepository: VocabRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordReviewUiState())
    val uiState: StateFlow<WordReviewUiState> = _uiState.asStateFlow()

    init {
        loadNextWord()
    }

    fun loadNextWord() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, answered = false, selectedOption = null, isCorrect = null, error = null) }
            try {
                val profile = profileRepository.getActive()
                if (profile == null) {
                    _uiState.update { it.copy(isLoading = false, error = "No active profile.") }
                    return@launch
                }
                val count = vocabRepository.countByProfile(profile.id)
                if (count < MIN_WORDS_REQUIRED) {
                    _uiState.update { it.copy(isLoading = false, tooFewWords = true) }
                    return@launch
                }
                val word = vocabRepository.getRandomForReview(profile.id)
                if (word == null) {
                    _uiState.update { it.copy(isLoading = false, tooFewWords = true) }
                    return@launch
                }
                val distractors = vocabRepository.getRandomDefinitions(profile.id, word.id, 3L)
                val options = (listOf(word.definition) + distractors).shuffled()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tooFewWords = false,
                        term = word.term,
                        options = options,
                        correctDefinition = word.definition,
                        currentWordId = word.id,
                        correctCount = word.correctCount,
                        incorrectCount = word.incorrectCount,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Something went wrong.") }
            }
        }
    }

    fun submitAnswer(option: String) {
        val state = _uiState.value
        if (state.answered || state.currentWordId < 0) return
        val correct = option == state.correctDefinition
        viewModelScope.launch {
            try { vocabRepository.recordAttempt(state.currentWordId, correct) } catch (_: Exception) { }
        }
        _uiState.update {
            it.copy(
                selectedOption = option,
                answered = true,
                isCorrect = correct,
                correctCount = if (correct) it.correctCount + 1 else it.correctCount,
                incorrectCount = if (!correct) it.incorrectCount + 1 else it.incorrectCount,
            )
        }
    }

    companion object {
        const val MIN_WORDS_REQUIRED = 4
    }
}
