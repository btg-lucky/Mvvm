package com.btg.common.network.cookie

import android.content.Context
import android.text.TextUtils
import com.orhanobut.logger.Logger
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** cookie 持久化存储：内存缓存 + SharedPreferences 序列化。 */
class PersistentCookieStore(context: Context) {

    private val cookies = HashMap<String, ConcurrentHashMap<String, Cookie>>()
    private val cookiePrefs = context.getSharedPreferences(COOKIE_PREFS, Context.MODE_PRIVATE)

    init {
        for ((host, value) in cookiePrefs.all) {
            val names = TextUtils.split(value as String, ",")
            for (name in names) {
                val encoded = cookiePrefs.getString(name, null) ?: continue
                val decoded = decodeCookie(encoded) ?: continue
                cookies.getOrPut(host) { ConcurrentHashMap() }[name] = decoded
            }
        }
    }

    fun add(url: HttpUrl, cookie: Cookie) {
        val name = getCookieToken(cookie)
        if (!cookie.persistent) {
            cookies.getOrPut(url.host) { ConcurrentHashMap() }[name] = cookie
        } else {
            cookies[url.host]?.remove(name)
        }
        val keys = cookies[url.host]?.keys ?: emptySet<String>()
        cookiePrefs.edit()
            .putString(url.host, TextUtils.join(",", keys))
            .putString(name, encodeCookie(OkHttpCookies(cookie)))
            .apply()
    }

    fun get(url: HttpUrl): List<Cookie> = cookies[url.host]?.values?.toList() ?: emptyList()

    fun removeAll(): Boolean {
        cookiePrefs.edit().clear().apply()
        cookies.clear()
        return true
    }

    fun remove(url: HttpUrl, cookie: Cookie): Boolean {
        val name = getCookieToken(cookie)
        val hostCookies = cookies[url.host] ?: return false
        if (!hostCookies.containsKey(name)) return false
        hostCookies.remove(name)
        val editor = cookiePrefs.edit()
        if (cookiePrefs.contains(name)) editor.remove(name)
        editor.putString(url.host, TextUtils.join(",", hostCookies.keys)).apply()
        return true
    }

    private fun getCookieToken(cookie: Cookie): String = "${cookie.name}@${cookie.domain}"

    private fun encodeCookie(cookie: OkHttpCookies?): String? {
        if (cookie == null) return null
        val os = ByteArrayOutputStream()
        return try {
            ObjectOutputStream(os).use { it.writeObject(cookie) }
            byteArrayToHexString(os.toByteArray())
        } catch (e: IOException) {
            Logger.e(e, "IOException in encodeCookie")
            null
        }
    }

    private fun decodeCookie(cookieString: String): Cookie? {
        val bytes = hexStringToByteArray(cookieString)
        return try {
            ObjectInputStream(ByteArrayInputStream(bytes)).use {
                (it.readObject() as OkHttpCookies).getCookies()
            }
        } catch (e: IOException) {
            Logger.e(e, "IOException in decodeCookie")
            null
        } catch (e: ClassNotFoundException) {
            Logger.e(e, "ClassNotFoundException in decodeCookie")
            null
        }
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            if (v < 16) sb.append('0')
            sb.append(Integer.toHexString(v))
        }
        return sb.toString().uppercase(Locale.US)
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private companion object {
        const val COOKIE_PREFS = "Cookies_prefs"
    }
}
