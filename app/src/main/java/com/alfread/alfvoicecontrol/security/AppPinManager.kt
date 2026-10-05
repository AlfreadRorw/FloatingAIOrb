package com.alfread.alfvoicecontrol.security

import com.alfread.alfvoicecontrol.data.LocalStore
import java.security.MessageDigest
import java.security.SecureRandom

class AppPinManager(private val store: LocalStore) {
    private val random = SecureRandom()

    suspend fun hasPin(): Boolean = store.getPinRecord() != null

    suspend fun setPin(pin: String) {
        require(pin.length in 4..6 && pin.all(Char::isDigit)) { "PIN must contain 4 to 6 digits." }
        val salt = ByteArray(16).also(random::nextBytes)
        val saltHex = salt.toHex()
        store.setPinRecord(saltHex, hash(pin, saltHex))
    }

    suspend fun verify(pin: String): Boolean {
        val record = store.getPinRecord() ?: return false
        return MessageDigest.isEqual(hash(pin, record.first).hexToBytes(), record.second.hexToBytes())
    }

    suspend fun clearPin() = store.setPinRecord(null, null)

    private fun hash(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salt = saltHex.hexToBytes()
        return digest.digest(salt + pin.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
