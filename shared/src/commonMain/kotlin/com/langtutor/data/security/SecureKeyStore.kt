package com.langtutor.data.security

/**
 * Cross-platform abstraction for encrypted key-value storage.
 * The Android implementation uses Android Keystore (AES-256-GCM) with a fresh random
 * IV per write; ciphertext is stored in private SharedPreferences.
 * No plaintext fallback: if the Keystore is unavailable, an exception is thrown.
 */
interface SecureKeyStore {
    suspend fun put(alias: String, value: String)
    suspend fun get(alias: String): String?
    suspend fun delete(alias: String)
}
