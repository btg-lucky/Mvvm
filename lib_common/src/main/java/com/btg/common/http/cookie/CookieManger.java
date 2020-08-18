package com.btg.common.http.cookie;

import android.content.Context;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * @创建者 567
 * @创建时间 2020/8/18 10:48 AM
 * @描述 cookie 管理器
 */
public class CookieManger implements CookieJar {

    private Context mContext;
    private PersistentCookieStore mCookieStore;

    public CookieManger(Context context) {
        mContext = context;
        if (mCookieStore == null){
            mCookieStore = new PersistentCookieStore(context);
        }
    }

    @NotNull
    @Override
    public List<Cookie> loadForRequest(@NotNull HttpUrl httpUrl) {
        return null;
    }

    @Override
    public void saveFromResponse(@NotNull HttpUrl httpUrl, @NotNull List<Cookie> list) {

    }
}
