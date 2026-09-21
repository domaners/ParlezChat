package com.langtutor.di

import com.langtutor.data.local.ErrorLogRepositoryImpl
import com.langtutor.data.local.ExplanationRepositoryImpl
import com.langtutor.data.local.LangTutorDatabase
import com.langtutor.data.local.MessageRepositoryImpl
import com.langtutor.data.local.ProfileRepositoryImpl
import com.langtutor.data.local.SettingsRepositoryImpl
import com.langtutor.data.local.VocabRepositoryImpl
import com.langtutor.data.remote.ClaudeClient
import com.langtutor.data.remote.KtorClaudeClient
import com.langtutor.data.security.ApiKeyStore
import com.langtutor.domain.repository.ErrorLogRepository
import com.langtutor.domain.repository.ExplanationRepository
import com.langtutor.domain.repository.MessageRepository
import com.langtutor.domain.repository.ProfileRepository
import com.langtutor.domain.repository.SettingsRepository
import com.langtutor.domain.repository.VocabRepository
import com.langtutor.domain.usecase.ExplainMessage
import com.langtutor.domain.usecase.SaveWord
import com.langtutor.domain.usecase.SendMessage
import com.langtutor.domain.usecase.StartConversation
import org.koin.dsl.module

val commonModule = module {
    // Security
    single { ApiKeyStore(get()) }

    // Remote
    single<ClaudeClient> { KtorClaudeClient(get(), get(), get()) }

    // Database (SqlDriver provided by platform module)
    single { LangTutorDatabase(get()) }

    // Repositories
    single<ProfileRepository> { ProfileRepositoryImpl(get()) }
    single<MessageRepository> { MessageRepositoryImpl(get()) }
    single<ExplanationRepository> { ExplanationRepositoryImpl(get()) }
    single<VocabRepository> { VocabRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<ErrorLogRepository> { ErrorLogRepositoryImpl(get()) }

    // Use cases (factory = new instance each time; they hold no state)
    factory { StartConversation(get(), get(), get()) }
    factory { SendMessage(get(), get(), get()) }
    factory { ExplainMessage(get(), get(), get()) }
    factory { SaveWord(get(), get(), get()) }
}
