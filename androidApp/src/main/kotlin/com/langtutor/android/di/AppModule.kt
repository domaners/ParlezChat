package com.langtutor.android.di

import com.langtutor.android.ui.changelanguage.ChangeLanguageViewModel
import com.langtutor.android.ui.chat.ChatViewModel
import com.langtutor.android.ui.dictionary.DictionaryViewModel
import com.langtutor.android.ui.errorlog.ErrorLogViewModel
import com.langtutor.android.ui.onboarding.OnboardingViewModel
import com.langtutor.android.ui.settings.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel {
        OnboardingViewModel(
            apiKeyStore = get(),
            claudeClient = get(),
            profileRepository = get(),
            settingsRepository = get(),
        )
    }
    viewModel {
        ChatViewModel(
            profileRepository = get(),
            messageRepository = get(),
            startConversation = get(),
            sendMessage = get(),
            explainMessage = get(),
            saveWord = get(),
            errorLogRepository = get(),
        )
    }
    viewModel {
        DictionaryViewModel(
            profileRepository = get(),
            vocabRepository = get(),
        )
    }
    viewModel {
        SettingsViewModel(
            profileRepository = get(),
            settingsRepository = get(),
            apiKeyStore = get(),
            claudeClient = get(),
        )
    }
    viewModel {
        ChangeLanguageViewModel(
            profileRepository = get(),
        )
    }
    viewModel {
        ErrorLogViewModel(
            errorLogRepository = get(),
        )
    }
}
