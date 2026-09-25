package com.wanderwildwood.soramoyo.station

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Weather Underground key, sealed with a key that lives in the phone's keystore and never
 * leaves it. The sealed form is what is written to the app's settings, so a copy of that file
 * — by any route — carries nothing that can be read without this phone.
 *
 * A key saved by an earlier version, in the clear, is still read, and is sealed where it lies
 * the first time it is.
 */
object Secrets {
    private const val ALIAS = "soramoyo-station-key"
    private const val SEALED = "k1:"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun isSealed(stored: String) = stored.startsWith(SEALED)

    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return SEALED + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /**
     * The key, or "" when it cannot be opened — the keystore key goes with the app and the
     * phone, so a settings file that outlived a reinstall or reached a new phone holds a key
     * nothing can open, and the owner types it again rather than the app failing to start.
     */
    fun open(stored: String): String {
        if (stored.isEmpty() || !isSealed(stored)) return stored
        return runCatching {
            val bytes = Base64.decode(stored.removePrefix(SEALED), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
