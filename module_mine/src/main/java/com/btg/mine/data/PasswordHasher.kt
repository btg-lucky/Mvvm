package com.btg.mine.data

import java.security.MessageDigest
import java.security.SecureRandom

/** 密码加盐哈希（纯函数，可单测）。存储格式：SHA-256(salt + password) 的十六进制。 */
object PasswordHasher {

    /** 生成 16 字节随机盐（32 位十六进制字符串）。 */
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + password).toByteArray(Charsets.UTF_8))
            .toHex()

    fun verify(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(
            hash(password, salt).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8),
        )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
