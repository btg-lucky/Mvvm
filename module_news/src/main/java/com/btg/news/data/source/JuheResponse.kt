package com.btg.news.data.source

import com.btg.common.network.AppException
import com.google.gson.annotations.SerializedName

/** 聚合数据统一响应外壳：{ error_code, reason, result }，error_code == 0 为成功。 */
data class JuheResponse<T>(
    @SerializedName("error_code") val errorCode: Int,
    val reason: String?,
    val result: T?,
)

/** 解包：成功返回 result；业务失败抛 Business（携带聚合错误码与 reason 文案）；成功但 result 为空抛 Parse。 */
fun <T> JuheResponse<T>.unwrap(): T = when {
    errorCode != 0 -> throw AppException.Business(errorCode, reason ?: "业务处理失败")
    result == null -> throw AppException.Parse("响应数据为空")
    else -> result
}
