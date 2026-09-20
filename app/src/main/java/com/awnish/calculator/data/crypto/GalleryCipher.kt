package com.awnish.calculator.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Versioned [nonce][ciphertext+tag] format.
 * The AES key never leaves Android Keystore.
 */
class GalleryCipher {
    private val alias = "awnish_gallery_key"
    private val ivSize = 12
    private val tagSize = 16
    private val bufferSize = 64 * 1024

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                        alias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
    }

    fun encrypt(input: InputStream, output: OutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        output.write(cipher.iv)

        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            val encrypted = cipher.update(buffer, 0, read)
            if (encrypted != null && encrypted.isNotEmpty()) output.write(encrypted)
        }

        val finalBytes = cipher.doFinal()
        if (finalBytes.isNotEmpty()) output.write(finalBytes)
    }

    fun decrypt(input: InputStream, output: OutputStream) {
        val iv = ByteArray(ivSize)
        readFully(input, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(tagSize * 8, iv)
        )

        val buffer = ByteArray(bufferSize)
        var pending = ByteArray(tagSize)
        var pendingSize = 0

        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break

            var offset = 0
            while (offset < read) {
                val available = read - offset
                val copyCount = minOf(available, tagSize - pendingSize)
                System.arraycopy(buffer, offset, pending, pendingSize, copyCount)
                pendingSize += copyCount
                offset += copyCount

                if (pendingSize == tagSize && offset < read) {
                    val encryptedChunkSize = read - offset
                    if (encryptedChunkSize > 0) {
                        val combined = ByteArray(tagSize + encryptedChunkSize)
                        System.arraycopy(pending, 0, combined, 0, tagSize)
                        System.arraycopy(buffer, offset, combined, tagSize, encryptedChunkSize)
                        pending = combined.copyOfRange(encryptedChunkSize, encryptedChunkSize + tagSize)
                        pendingSize = tagSize

                        val ciphertextPart = combined.copyOfRange(0, encryptedChunkSize)
                        val plain = cipher.update(ciphertextPart)
                        if (plain != null && plain.isNotEmpty()) output.write(plain)
                        offset = read
                    }
                }
            }
        }

        require(pendingSize == tagSize) { "Invalid encrypted file" }
        val finalBytes = cipher.doFinal(pending)
        if (finalBytes.isNotEmpty()) output.write(finalBytes)
    }

    fun encrypt(bytes: ByteArray): ByteArray {
        return java.io.ByteArrayInputStream(bytes).use { input ->
            val output = java.io.ByteArrayOutputStream(bytes.size + ivSize + tagSize)
            encrypt(input, output)
            output.toByteArray()
        }
    }

    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size > ivSize + tagSize) { "Invalid encrypted file" }
        return java.io.ByteArrayInputStream(bytes).use { input ->
            val output = java.io.ByteArrayOutputStream(bytes.size - ivSize - tagSize)
            decrypt(input, output)
            output.toByteArray()
        }
    }

    private fun readFully(input: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read < 0) error("Invalid encrypted file")
            if (read == 0) continue
            offset += read
        }
    }
}
