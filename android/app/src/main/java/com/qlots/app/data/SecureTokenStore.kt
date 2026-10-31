package com.qlots.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small app-private token store. The AES key is non-exportable and protected by Android Keystore. */
class SecureTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("qlots_secure_session", Context.MODE_PRIVATE)

    @Synchronized
    fun read(): String? {
        val encoded = preferences.getString(TOKEN_KEY, null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_BYTES)
            val iv = packed.copyOfRange(0, IV_BYTES)
            val ciphertext = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            runCatching { deleteKey() }
            null
        }
    }

    @Synchronized
    fun write(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ciphertext
        check(preferences.edit().putString(TOKEN_KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not persist the encrypted Qlots session."
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(TOKEN_KEY).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun deleteKey() {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "qlots.session.aes-gcm.v1"
        const val TOKEN_KEY = "encrypted_bearer_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
