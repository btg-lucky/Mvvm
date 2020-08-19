package com.btg.common.http.convert;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.lang.reflect.GenericSignatureFormatError;

import okhttp3.ResponseBody;
import retrofit2.Converter;

/**
 * @创建者 567
 * @创建时间 2020/8/19 8:24 PM
 * @描述 自定义Converter 处理json返回值中不需要或者是code错误的部分
 */
public class MyGsonResponseBodyConverter<T> implements Converter<ResponseBody, T> {

    private Gson           mGson;
    private TypeAdapter<T> mAdapter;

    public MyGsonResponseBodyConverter(Gson gson, TypeAdapter<T> adapter) {
        mGson = gson;
        mAdapter = adapter;
    }

    @Override
    public T convert(ResponseBody value) throws IOException {
        //todo 此处处理逻辑 暂时和GsonResponseBodyConverter一致
        JsonReader jsonReader = mGson.newJsonReader(value.charStream());
        try {
            T result = mAdapter.read(jsonReader);
            if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonIOException("JSON document was not fully consumed.");
            }
            return result;
        } finally {
            value.close();
        }
    }
}
