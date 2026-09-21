package com.langtutor.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.langtutor.data.remote.ClaudeError
import com.langtutor.domain.model.ChatMessage
import com.langtutor.domain.model.Explanation
import com.langtutor.domain.model.LearnerProfile
import com.langtutor.domain.model.Role
import com.langtutor.domain.model.VocabEntry
import com.langtutor.domain.repository.ErrorLogRepository
import com.langtutor.domain.repository.MessageRepository
import com.langtutor.domain.repository.ProfileRepository
import com.langtutor.domain.usecase.ExplainMessage
import com.langtutor.domain.usecase.SaveWord
import com.langtutor.domain.usecase.SendMessage
import com.langtutor.domain.usecase.StartConversation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatUiState(
    val profile: LearnerProfile? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val isSendEnabled: Boolean = true,
    val isSavingWord: Boolean = false,
    val explanation: ExplanationSheetState? = null,
    val banner: BannerState? = null,
    val savedWordToast: VocabEntry? = null,
)

data class ExplanationSheetState(
    val message: ChatMessage,
    val explanation: Explanation? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class BannerState(
    val message: String,
    val showFixInSettings: Boolean = false,
)

class ChatViewModel(
    private val profileRepository: ProfileRepository,
    private val messageRepository: MessageRepository,
    private val startConversation: StartConversation,
    private val sendMessage: SendMessage,
    private val explainMessage: ExplainMessage,
    private val saveWord: SaveWord,
    private val errorLogRepository: ErrorLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Cancelled and restarted whenever the active profile changes, so only one message
    // observer is ever alive at a time (avoids accumulation across language switches).
    private var messageObserverJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                profileRepository.observeActive().collectLatest { profile ->
                    // Reset message list and disable send immediately on any profile change
                    // to prevent sending with a stale profile during the load window.
                    _uiState.update { it.copy(profile = profile, messages = emptyList(), isSendEnabled = false) }
                    if (profile != null) observeMessages(profile)
                }
            } catch (e: Exception) {
                logError("ProfileObserver", e)
                _uiState.update { it.copy(banner = BannerState("Failed to load profile. Please restart the app.")) }
            }
        }
    }

    private fun observeMessages(profile: LearnerProfile) {
        messageObserverJob?.cancel()
        messageObserverJob = viewModelScope.launch {
            try {
                messageRepository.observeByProfile(profile.id).collectLatest { messages ->
                    _uiState.update { it.copy(messages = messages) }
                    if (messages.isEmpty()) {
                        kickOff(profile)  // kickOff re-enables send after the API call
                    } else {
                        _uiState.update { it.copy(isSendEnabled = true) }
                    }
                }
            } catch (e: Exception) {
                logError("MessageObserver", e)
                _uiState.update { it.copy(banner = BannerState("Failed to load messages.")) }
            }
        }
    }

    private fun kickOff(profile: LearnerProfile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTyping = true, isSendEnabled = false) }
            try {
                startConversation(profile).onFailure { err ->
                    _uiState.update { it.copy(banner = mapError(err)) }
                }
            } catch (e: Exception) {
                logError("KickOff", e)
                _uiState.update { it.copy(banner = BannerState("Something went wrong. Please retry.")) }
            }
            _uiState.update { it.copy(isTyping = false, isSendEnabled = true) }
        }
    }

    fun send(text: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isTyping = true, isSendEnabled = false) }
            try {
                sendMessage(profile, text).onFailure { err ->
                    _uiState.update { it.copy(banner = mapError(err)) }
                }
            } catch (e: Exception) {
                logError("SendMessage", e)
                _uiState.update { it.copy(banner = BannerState("Something went wrong. Please retry.")) }
            }
            _uiState.update { it.copy(isTyping = false, isSendEnabled = true) }
        }
    }

    fun retry(message: ChatMessage) {
        send(message.content)
    }

    fun openExplanation(message: ChatMessage) {
        _uiState.update { it.copy(explanation = ExplanationSheetState(message, isLoading = true)) }
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            try {
                explainMessage(profile, message.id, message.content).fold(
                    onSuccess = { explanation ->
                        _uiState.update { s -> s.copy(explanation = s.explanation?.copy(explanation = explanation, isLoading = false)) }
                    },
                    onFailure = {
                        _uiState.update { s -> s.copy(explanation = s.explanation?.copy(isLoading = false, error = "Couldn't generate an explanation.")) }
                    },
                )
            } catch (e: Exception) {
                _uiState.update { s -> s.copy(explanation = s.explanation?.copy(isLoading = false, error = "Couldn't generate an explanation.")) }
            }
        }
    }

    fun closeExplanation() {
        _uiState.update { it.copy(explanation = null) }
    }

    fun saveWord(term: String, contextMessage: ChatMessage) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
            try {
                saveWord(profile, term, contextMessage.content, contextMessage.id).fold(
                    onSuccess = { entry -> _uiState.update { it.copy(savedWordToast = entry) } },
                    onFailure = { _uiState.update { it.copy(banner = BannerState("Couldn't save \"$term\".")) } },
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(banner = BannerState("Couldn't save \"$term\".")) }
            }
        }
    }

    /** Save a word typed directly by the user, using the most recent assistant message as context. */
    fun saveWordFromInput(term: String) {
        val profile = _uiState.value.profile ?: return
        val contextMsg = _uiState.value.messages.lastOrNull { it.role == Role.ASSISTANT }
        _uiState.update { it.copy(isSavingWord = true) }
        viewModelScope.launch {
            try {
                saveWord(
                    profile = profile,
                    term = term,
                    contextMessage = contextMsg?.content ?: "",
                    sourceMessageId = contextMsg?.id,
                ).fold(
                    onSuccess = { entry -> _uiState.update { it.copy(savedWordToast = entry, isSavingWord = false) } },
                    onFailure = { err -> _uiState.update { it.copy(banner = BannerState("Couldn't save \"$term\"."), isSavingWord = false) } },
                )
            } catch (e: Exception) {
                logError("SaveWordFromInput", e)
                _uiState.update { it.copy(banner = BannerState("Couldn't save \"$term\"."), isSavingWord = false) }
            }
        }
    }

    fun dismissSavedWordToast() {
        _uiState.update { it.copy(savedWordToast = null) }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(banner = null) }
    }

    private fun mapError(err: Throwable) = when (err) {
        is ClaudeError.InvalidApiKey -> BannerState("Your API key is no longer valid.", showFixInSettings = true)
        is ClaudeError.RateLimited -> BannerState("Rate limited — please wait a moment and retry.")
        is ClaudeError.Network -> BannerState("Network error. Check your connection and retry.")
        is ClaudeError.BadRequest -> BannerState("The request was rejected. Check the error log for details.")
        is ClaudeError.Overloaded -> BannerState("Anthropic is temporarily overloaded. Please retry.")
        else -> {
            viewModelScope.launch { logError("Exception", err) }
            BannerState("Something went wrong. Please retry.")
        }
    }

    private suspend fun logError(type: String, err: Throwable) {
        runCatching {
            errorLogRepository.log(
                errorType = type,
                message = "${err::class.simpleName}: ${err.message ?: err.toString()}",
            )
        }
    }
}
