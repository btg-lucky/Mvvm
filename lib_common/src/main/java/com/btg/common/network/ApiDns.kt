package com.btg.common.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/** IPv4 排到 IPv6 前，缓解部分网络下请求慢的问题。 */
class ApiDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val ordered = ArrayList<InetAddress>()
            for (address in InetAddress.getAllByName(hostname)) {
                if (address is Inet4Address) ordered.add(0, address) else ordered.add(address)
            }
            ordered
        } catch (e: NullPointerException) {
            throw UnknownHostException("Broken system behaviour").apply { initCause(e) }
        }
    }
}
