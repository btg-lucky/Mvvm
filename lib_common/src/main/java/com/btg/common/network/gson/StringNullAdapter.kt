package com.btg.common.network.gson

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/** 后台返回 null 或 "null" 字符串时兜底为空字符串。 */
class StringNullAdapter : TypeAdapter<String>() {
    override fun read(reader: JsonReader): String {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return ""
        }
        val jsonStr = reader.nextString()
        return if (jsonStr == "null") "" else jsonStr
    }

    override fun write(writer: JsonWriter, value: String?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.value(value)
    }
}
