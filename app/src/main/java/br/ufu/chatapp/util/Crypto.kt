package br.ufu.chatapp.util

import android.util.Base64
import br.ufu.chatapp.BuildConfig
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val LEGACY_TRANSFORMATION = "AES/ECB/PKCS5Padding"
    private const val IV_SIZE = 12
    private const val TAG_LENGTH = 128

    private fun keySpec(): SecretKeySpec {
        return SecretKeySpec(BuildConfig.MSG_CRYPTO_KEY.toByteArray(), ALGORITHM)
    }

    fun encrypt(text: String): String {
        if (text.isBlank()) return text
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, keySpec(), GCMParameterSpec(TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isBlank()) return cipherText
        return runCatching {
            val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
            val iv = decoded.copyOfRange(0, IV_SIZE)
            val payload = decoded.copyOfRange(IV_SIZE, decoded.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), GCMParameterSpec(TAG_LENGTH, iv))
            String(cipher.doFinal(payload))
        }.recoverCatching {
            val cipher = Cipher.getInstance(LEGACY_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keySpec())
            val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
            String(cipher.doFinal(decoded))
        }.getOrElse { cipherText }
    }
}
