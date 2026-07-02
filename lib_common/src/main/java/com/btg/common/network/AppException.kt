package com.btg.common.network

/**
 * 应用统一异常。数据层把各种底层异常映射到这里，UI 层拿到的都是带友好文案的 AppException。
 */
sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 无网络 / 连接失败。 */
    class Network(message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 请求超时。 */
    class Timeout(message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 服务器返回 HTTP 错误码（4xx/5xx）。 */
    class Server(val httpCode: Int, message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 业务码非成功（HTTP 200 但 body.code 表示失败）。 */
    class Business(val code: Int, message: String) : AppException(message)

    /** 解析失败 / 数据格式异常。 */
    class Parse(message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 其他未归类异常。 */
    class Unknown(message: String, cause: Throwable? = null) : AppException(message, cause)
}
