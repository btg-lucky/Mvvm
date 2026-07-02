package com.btg.common.network

/** 业务成功码默认值。不同后端可在 unwrap 处传入实际成功码。 */
const val CODE_SUCCESS: Int = 0

/**
 * 统一响应包装。真实后端字段名不同（如 error_code/reason/result）时，
 * 在具体 data class 上用 @SerializedName 映射到 code/message/data。
 */
data class BaseResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?,
)

/**
 * 解包：业务码成功且 data 非空返回 data；业务码失败抛 [AppException.Business]；
 * 成功但 data 为空抛 [AppException.Parse]。
 */
fun <T> BaseResponse<T>.unwrap(successCode: Int = CODE_SUCCESS): T = when {
    code != successCode -> throw AppException.Business(code, message ?: "业务处理失败")
    data == null -> throw AppException.Parse("响应数据为空")
    else -> data
}
