package com.btg.common.http.api;

import android.text.TextUtils;

import com.btg.common.base.BaseApplication;
import com.btg.common.base.BaseContent;
import com.btg.common.http.cookie.CookieManger;
import com.btg.common.http.gson.DoubleDefaultAdapter;
import com.btg.common.http.gson.IntegerDefaultAdapter;
import com.btg.common.http.gson.LongDefaultAdapter;
import com.btg.common.http.gson.StringNullAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.orhanobut.logger.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * @创建者 567
 * @创建时间 2020/8/17 17:05
 * @描述 用来获取Retrofit对象，根据不同的baseUrl保存同的ApiRetrofit对象，根据不同的ApiService获得不同的Retrofit对象
 * 之后的版本会改用缓存做
 */
public class ApiRetrofit {

    private static final String      LOG_TAG         = "ApiRetrofit %s";
    private static final int         DEFAULT_TIMEOUT = 15;
    private static       ApiRetrofit sApiRetrofit;

    private String                mBaseUrl     = BaseContent.BASE_URL;
    private Map<String, Retrofit> mRetrofitMap = new HashMap<>();
    private Map<String, ?>        mServiceMap  = new HashMap<>();

    private Retrofit             mRetrofit;
    private Gson                 mGson;
    private OkHttpClient.Builder httpClintBuilder = new OkHttpClient.Builder();

    /**
     * 获取ApiRetrofit单例对象
     *
     * @return ApiRetrofit
     */
    public static ApiRetrofit getInstance() {
        synchronized (sApiRetrofit){
            if (sApiRetrofit == null) {
                sApiRetrofit = new ApiRetrofit();
            }
            return sApiRetrofit;
        }
    }

    private ApiRetrofit() {
        httpClintBuilder.cookieJar(new CookieManger(BaseApplication.getApplication()))
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);//错误重联
    }

    /**
     * 根据传入的baseUrl创建Retrofit
     *
     * @param baseUrl baseUrl
     * @return Retrofit
     */
    public Retrofit initRetrofit(String baseUrl) {
        if (!TextUtils.isEmpty(baseUrl)) {
            mBaseUrl = baseUrl;
        }
        return initRetrofit();
    }

    /**
     * 创建Retrofit对象
     *
     * @return Retrofit
     */
    public Retrofit initRetrofit() {
        if (mRetrofitMap.containsKey(mBaseUrl)) {
            mRetrofit = mRetrofitMap.get(mBaseUrl);
        } else {
            mRetrofit = new Retrofit.Builder()
                    .baseUrl(mBaseUrl)
                    .addConverterFactory(GsonConverterFactory.create(buildGson()))//添加json转换框架(正常转换框架)
                    //支持RxJava2
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .client(httpClintBuilder.build())
                    .build();
            mRetrofitMap.put(mBaseUrl, mRetrofit);
        }
        Logger.d(LOG_TAG, "创建Retrofit对象");
        return mRetrofit;
    }


    /**
     * 根据传入的Class,获取对应的 Retrofit Service
     *
     * @param serviceClass ApiService Class
     * @param <T>          ApiService
     * @return ApiService
     */
    public synchronized <T> T createService(Class<T> serviceClass) {
        Logger.d(LOG_TAG, serviceClass.getSimpleName());
        if (mServiceMap.containsKey(serviceClass.getName())){
            return (T)mServiceMap.get(serviceClass.getName());
        }else {
            return mRetrofit.create(serviceClass);
        }
    }

    /**
     * 增加后台返回""和"null"的处理,如果后台返回格式正常
     * 1.int=>0
     * 2.double=>0.00
     * 3.long=>0L
     * 4.String=>""
     *
     * @return mGson
     */
    public Gson buildGson() {
        if (mGson == null) {
            mGson = new GsonBuilder()
                    .registerTypeAdapter(Integer.class, new IntegerDefaultAdapter())
                    .registerTypeAdapter(int.class, new IntegerDefaultAdapter())
                    .registerTypeAdapter(Double.class, new DoubleDefaultAdapter())
                    .registerTypeAdapter(double.class, new DoubleDefaultAdapter())
                    .registerTypeAdapter(Long.class, new LongDefaultAdapter())
                    .registerTypeAdapter(long.class, new LongDefaultAdapter())
                    .registerTypeAdapter(String.class, new StringNullAdapter())
                    .create();
        }
        return mGson;
    }

}
