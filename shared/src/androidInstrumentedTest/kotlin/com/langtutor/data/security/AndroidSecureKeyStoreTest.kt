package com.langtutor.data.security

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Instrumented test — requires a real device or emulator because it exercises the Android Keystore.
 * Run with: ./gradlew :shared:connectedAndroidTest
 */
class AndroidSecureKeyStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AndroidSecureKeyStore(context)

    @Test
    fun encryptDecryptRoundTrip() = runTest {
        val alias = "test_key_${System.currentTimeMillis()}"
        try {
            store.put(alias, "super-secret-value")
            val retrieved = store.get(alias)
            assertEquals("super-secret-value", retrieved)
        } finally {
            store.delete(alias)
        }
    }

    @Test
    fun deleteRemovesValue() = runTest {
        val alias = "test_delete_${System.currentTimeMillis()}"
        store.put(alias, "to-be-deleted")
        store.delete(alias)
        assertNull(store.get(alias))
    }

    @Test
    fun getNonExistentReturnsNull() = runTest {
        assertNull(store.get("this_alias_does_not_exist"))
    }
}
