package com.btg.common.http.cookie;

import android.content.Context;
import com.orhanobut.logger.Logger;
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
    private static final String LOG_TAG = "CookieManger %d";

    private Context mContext;
    private PersistentCookieStore mCookieStore;

    public CookieManger(Context context) {
        mContext = context;
        if (mCookieStore == null){
            mCookieStore = new PersistentCookieStore(context);
        }
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        if (cookies != null && cookies.size() > 0) {
            for (Cookie item : cookies) {
                mCookieStore.add(url, item);
            }
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> cookies = mCookieStore.get(url);
        Logger.d(LOG_TAG, cookies);
        return cookies;
    }
}
