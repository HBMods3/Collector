package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive GitHub OAuth access tokens using Android Keystore AES-256-GCM.
 * Falls back cleanly in non-Keystore JVM unit test environments.
 */
object TokenCryptoManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "slr_note_bu_token_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PREFIX_KS = "KS1:"
    private const val PREFIX_PLAIN_TEST = "TST:"

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    fun encrypt(plainText: String): String {
        val key = getOrCreateKey()
        if (key == null) {
            val b64 = Base64.encodeToString(plainText.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            return "$PREFIX_PLAIN_TEST$b64"
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(1 + iv.size + encrypted.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
        return PREFIX_KS + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String? {
        return try {
            when {
                cipherText.startsWith(PREFIX_KS) -> {
                    val key = getOrCreateKey() ?: return null
                    val raw = Base64.decode(cipherText.removePrefix(PREFIX_KS), Base64.NO_WRAP)
                    val ivLen = raw[0].toInt() and 0xFF
                    val iv = raw.copyOfRange(1, 1 + ivLen)
                    val payload = raw.copyOfRange(1 + ivLen, raw.size)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    String(cipher.doFinal(payload), StandardCharsets.UTF_8)
                }
                cipherText.startsWith(PREFIX_PLAIN_TEST) -> {
                    val raw = Base64.decode(cipherText.removePrefix(PREFIX_PLAIN_TEST), Base64.NO_WRAP)
                    String(raw, StandardCharsets.UTF_8)
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun sha256Hex(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
