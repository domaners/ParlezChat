package com.langtutor.di

import com.langtutor.data.local.DatabaseDriverFactory
import com.langtutor.data.security.AndroidSecureKeyStore
import com.langtutor.data.security.SecureKeyStore
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val androidModule = module {
    // Secure key store — wraps Android Keystore
    single<SecureKeyStore> { AndroidSecureKeyStore(androidContext()) }

    // SQLite driver — creates langtutor.db with foreign keys enabled
    single { DatabaseDriverFactory(androidContext()).create() }

    // Ktor HTTP client with OkHttp engine
    // Timeouts: connect 10 s, read/write 60 s (long for AI responses)
    // x-api-key header is sanitized out of logs
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.HEADERS
                sanitizeHeader { header -> header.equals("x-api-key", ignoreCase = true) }
            }
            engine {
                config {
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    writeTimeout(60, TimeUnit.SECONDS)
                }
            }
        }
    }
}
