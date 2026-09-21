package com.langtutor.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts values using AES-256-GCM with a non-exportable key stored in the Android Keystore.
 * A fresh random 12-byte IV is generated for every [put] call. The serialized payload is
 * `Base64(iv || ciphertext)` stored in private SharedPreferences.
 *
 * android:allowBackup="false" must be set in the manifest, and the key file must be excluded
 * from any backup/transfer rules — a Keystore key cannot survive a restore, which would make
 * the ciphertext permanently unreadable.
 *
 * If the Keystore is unavailable (e.g. device not yet unlocked), the JCA operations will
 * throw and the exception propagates to the caller; there is no plaintext fallback.
 */
class AndroidSecureKeyStore(context: Context) : SecureKeyStore {

    private val prefs = context.getSharedPreferences("langtutor_secure", Context.MODE_PRIVATE)
    private val androidKeyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    override suspend fun put(alias: String, value: String) = withContext(Dispatchers.IO) {
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv                                 // 12 bytes (GCM default)
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = iv + ciphertext
        prefs.edit().putString(alias, Base64.encodeToString(combined, Base64.NO_WRAP)).apply()
    }

    override suspend fun get(alias: String): String? = withContext(Dispatchers.IO) {
        val encoded = prefs.getString(alias, null) ?: return@withContext null
        val key = getKeyIfExists(alias) ?: return@withContext null   // key lost after restore
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    override suspend fun delete(alias: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(alias).apply()
        if (androidKeyStore.containsAlias(alias)) {
            androidKeyStore.deleteEntry(alias)
        }
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        if (!androidKeyStore.containsAlias(alias)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").also { kg ->
                kg.init(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                kg.generateKey()
            }
        }
        return androidKeyStore.getKey(alias, null) as SecretKey
    }

    private fun getKeyIfExists(alias: String): SecretKey? {
        if (!androidKeyStore.containsAlias(alias)) return null
        return androidKeyStore.getKey(alias, null) as? SecretKey
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
