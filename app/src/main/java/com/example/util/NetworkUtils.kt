package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * ネットワーク接続状態（Wi-Fi, モバイルデータ通信, オフライン）の判定ユーティリティ。
 */
object NetworkUtils {

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getConnectionTypeName(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "未接続"
        val activeNetwork = cm.activeNetwork ?: return "オフライン"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "オフライン"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi 接続中"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "モバイル通信 (4G/5G)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有線LAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth テザリング"
            else -> "接続中"
        }
    }
}
