package com.btg.common.network.cookie

import okhttp3.Cookie
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/** Cookie 的可序列化包装（手动读写字段，兼容 Java 序列化流）。 */
class OkHttpCookies(@Transient private val cookies: Cookie) : Serializable {

    @Transient
    private var clientCookies: Cookie? = null

    fun getCookies(): Cookie = clientCookies ?: cookies

    @Throws(IOException::class)
    private fun writeObject(out: ObjectOutputStream) {
        out.writeObject(cookies.name)
        out.writeObject(cookies.value)
        out.writeLong(cookies.expiresAt)
        out.writeObject(cookies.domain)
        out.writeObject(cookies.path)
        out.writeBoolean(cookies.secure)
        out.writeBoolean(cookies.httpOnly)
        out.writeBoolean(cookies.hostOnly)
        out.writeBoolean(cookies.persistent)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(input: ObjectInputStream) {
        val name = input.readObject() as String
        val value = input.readObject() as String
        val expiresAt = input.readLong()
        val domain = input.readObject() as String
        val path = input.readObject() as String
        val secure = input.readBoolean()
        val httpOnly = input.readBoolean()
        val hostOnly = input.readBoolean()
        input.readBoolean() // persistent：读出以对齐字节流，Cookie.Builder 无对应 setter

        var builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
        builder = if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        builder = builder.path(path)
        if (secure) builder = builder.secure()
        if (httpOnly) builder = builder.httpOnly()
        clientCookies = builder.build()
    }
}
