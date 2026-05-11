package com.samwise.unshelved.core.network

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object PkceUtils {
    fun generateVerifier(): String {
        val bytes = ByteArray(42)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateState(): String = UUID.randomUUID().toString()
}
