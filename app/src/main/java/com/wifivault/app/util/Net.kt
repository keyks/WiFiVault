package com.wifivault.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

object Net {

    /**
     * 返回最可能被其他设备访问的局域网 IPv4：
     * 热点 AP > WiFi > 以太网 > USB 网络，并优先私有网段，避免误用蜂窝 / VPN 地址。
     */
    fun localIpAddress(): String? {
        var best: String? = null
        var bestScore = Int.MIN_VALUE
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement() ?: continue
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") ||
                    name.startsWith("rmnet") || name.startsWith("ccmni") ||
                    name.startsWith("clat")
                ) {
                    continue
                }
                val interfaceScore = when {
                    name.startsWith("ap") || name.startsWith("swlan") ||
                        name.startsWith("softap") -> 500
                    name.startsWith("wlan") -> 400
                    name.startsWith("eth") -> 300
                    name.startsWith("rndis") || name.startsWith("usb") -> 200
                    else -> 0
                }
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement() ?: continue
                    if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr !is Inet4Address) continue
                    val host = addr.hostAddress ?: continue
                    val score = interfaceScore + if (isPrivateIpv4(host)) 100 else 0
                    if (score > bestScore) {
                        bestScore = score
                        best = host
                    }
                }
            }
        } catch (e: Exception) {
            return best
        }
        return best
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun portInUse(port: Int): Boolean = try {
        ServerSocket(port).use { false }
    } catch (e: Exception) {
        true
    }

    /** 去掉 http/https 前缀与结尾斜杠，只留下「IP:端口」，方便用户在其他设备上手动输入。 */
    fun hostOnly(address: String?): String? {
        val text = address?.trim().orEmpty()
        if (text.isEmpty()) return null
        return text.removePrefix("https://").removePrefix("http://").trimEnd('/').ifEmpty { null }
    }
}
