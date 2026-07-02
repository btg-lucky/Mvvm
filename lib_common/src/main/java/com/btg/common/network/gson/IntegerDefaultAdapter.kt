package com.btg.common.network.gson

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonSyntaxException
import java.lang.reflect.Type

/** 后台返回 "" 或 "null" 时兜底为 0。 */
class IntegerDefaultAdapter : JsonSerializer<Int>, JsonDeserializer<Int> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Int {
        try {
            val s = json.asString
            if (s == "" || s == "null") return 0
        } catch (ignore: Exception) {
        }
        return try {
            json.asInt
        } catch (e: NumberFormatException) {
            throw JsonSyntaxException(e)
        }
    }

    override fun serialize(src: Int, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonPrimitive(src)
}
