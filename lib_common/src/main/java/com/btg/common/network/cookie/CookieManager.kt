package com.btg.common.network.cookie

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** OkHttp CookieJar 实现，委托 [PersistentCookieStore] 做持久化。 */
class CookieManager(context: Context) : CookieJar {

    private val cookieStore = PersistentCookieStore(context)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            cookieStore.add(url, cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore.get(url)
}
