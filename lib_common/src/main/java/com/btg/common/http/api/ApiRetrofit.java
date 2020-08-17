package com.btg.common.http.api;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Retrofit;

/**
 * @创建者 567
 * @创建时间 2020/8/17 17:05
 * @描述
 */
public class ApiRetrofit {

    private static final String            TAG              = "ApiRetrofit";
    private static final int               DEFAULT_TIMEOUT  = 15;
    private static       ApiRetrofit       sApiRetrofit;
    private static       List<ApiRetrofit> mApiRetrofitList = new ArrayList<>();
    private static       List<Retrofit>    mRetrofitList    = new ArrayList<>();
    private              Retrofit          mRetrofit;
    private              Gson              mGson;


}
