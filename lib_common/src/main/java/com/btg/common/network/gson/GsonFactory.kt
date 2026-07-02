package com.btg.common.network.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * 统一构建注册了空值兜底适配器的 Gson。
 * 兜底规则：后台返回 "" / "null" / null 时，int→0、double→0.0、long→0L、String→""，
 * 避免单个脏字段导致整条解析失败。NetworkModule 与测试共用此工厂。
 */
object GsonFactory {
    fun create(): Gson = GsonBuilder()
        .registerTypeAdapter(Int::class.javaObjectType, IntegerDefaultAdapter())
        .registerTypeAdapter(Int::class.javaPrimitiveType, IntegerDefaultAdapter())
        .registerTypeAdapter(Double::class.javaObjectType, DoubleDefaultAdapter())
        .registerTypeAdapter(Double::class.javaPrimitiveType, DoubleDefaultAdapter())
        .registerTypeAdapter(Long::class.javaObjectType, LongDefaultAdapter())
        .registerTypeAdapter(Long::class.javaPrimitiveType, LongDefaultAdapter())
        .registerTypeAdapter(String::class.java, StringNullAdapter())
        .create()
}
