package com.langtutor.data.security

/**
 * Single point of access for the user's Claude API key.
 * The key is never persisted in plaintext and never included in the
 * SQLite database or any logs (see KtorClaudeClient for header sanitization).
 */
class ApiKeyStore(private val keyStore: SecureKeyStore) {

    private val alias = "claude_api_key"

    suspend fun store(key: String) = keyStore.put(alias, key)

    suspend fun get(): String? = keyStore.get(alias)

    suspend fun delete() = keyStore.delete(alias)

    suspend fun has(): Boolean = keyStore.get(alias) != null
}
