package com.example.ocr_finace.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

class HomeNetworkManager(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    @SuppressLint("MissingPermission", "Deprecation")
    fun currentSsid(): String? = runCatching {
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val connectionSsid = wifiManager.connectionInfo?.ssid
        val ssid = normalizeSsid(connectionSsid) ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (capabilities.transportInfo as? WifiInfo)?.ssid
        } else null
        normalizeSsid(ssid)
    }.getOrNull()

    fun isConnectedTo(ssid: String): Boolean =
        ssid.isNotBlank() && currentSsid() == normalizeSsid(ssid)
}

internal fun normalizeSsid(value: String?): String? = value
    ?.trim()
    ?.removeSurrounding("\"")
    ?.takeUnless { it.isBlank() || it.equals("<unknown ssid>", ignoreCase = true) }
