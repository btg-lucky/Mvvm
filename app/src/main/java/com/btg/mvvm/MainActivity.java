package com.btg.mvvm;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.btg.common.http.api.ApiRetrofit;
import com.btg.common.http.api.ApiService;

/**
 * @创建者 567
 * @创建时间 2020/8/14 15:00
 * @描述
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ApiService s = ApiRetrofit.getInstance().initRetrofit("").create(ApiService.class);
    }
}
