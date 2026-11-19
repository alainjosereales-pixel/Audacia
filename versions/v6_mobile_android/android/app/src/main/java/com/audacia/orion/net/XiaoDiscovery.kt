package com.audacia.orion.net

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

const val BEACON_PREFIX = "ORION_XIAO_CAM:"
const val UDP_BEACON_PORT = 9999

fun parseBeacon(msg: String): XiaoEndpoint? {
    if (!msg.startsWith(BEACON_PREFIX)) return null
    val remainder = msg.removePrefix(BEACON_PREFIX).trim()
    val parts = remainder.split(":")
    if (parts.size != 2) return null

    val ip = parts[0].trim()
    val portStr = parts[1].trim()

    // Validar IPv4
    val octets = ip.split(".")
    if (octets.size != 4) return null
    for (oct in octets) {
        val num = oct.toIntOrNull() ?: return null
        if (num !in 0..255) return null
    }

    // Validar puerto
    val port = portStr.toIntOrNull() ?: return null
    if (port !in 1..65535) return null

    return XiaoEndpoint(ip = ip, streamPort = port)
}

class XiaoDiscovery(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1000, TimeUnit.MILLISECONDS)
        .readTimeout(1000, TimeUnit.MILLISECONDS)
        .build()
) {
    private val prefs by lazy {
        context.getSharedPreferences("orion_net_prefs", Context.MODE_PRIVATE)
    }

    fun getLastKnownEndpoint(): XiaoEndpoint? {
        return try {
            val ip = prefs.getString("last_ip", null) ?: return null
            val port = prefs.getInt("last_port", 81)
            XiaoEndpoint(ip, port)
        } catch (e: Exception) {
            null
        }
    }

    fun saveLastKnownEndpoint(endpoint: XiaoEndpoint) {
        try {
            prefs.edit()
                .putString("last_ip", endpoint.ip)
                .putInt("last_port", endpoint.streamPort)
                .apply()
        } catch (e: Exception) {
            // Ignorar en entornos sin almacenamiento
        }
    }
    fun listenBeacons(port: Int = UDP_BEACON_PORT): Flow<XiaoEndpoint> = flow {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("orion_beacon_lock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1500
                bind(InetSocketAddress(port))
            }
            val buffer = ByteArray(256)
            val packet = DatagramPacket(buffer, buffer.size)

            while (coroutineContext.isActive) {
                try {
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val endpoint = parseBeacon(msg)
                    if (endpoint != null) {
                        emit(endpoint)
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Timeout esperado para comprobar coroutineContext.isActive
                } catch (e: java.io.IOException) {
                    // Ignorar errores transitorios de red (ej. PortUnreachable) y continuar escuchando
                    kotlinx.coroutines.delay(200)
                }
            }
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
            try { multicastLock?.release() } catch (ignored: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    suspend fun probe(endpoint: XiaoEndpoint): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${endpoint.controlUrl}/whoami")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
