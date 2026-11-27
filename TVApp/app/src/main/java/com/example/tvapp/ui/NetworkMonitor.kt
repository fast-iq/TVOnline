package com.example.tvapp.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Монитор доступности сети через default-network callback. При пропаже связи
 * показываем баннер «офлайн — список из кэша», чтобы пустая/устаревшая сетка
 * не выглядела поломкой. Колбэк приходит в главный поток (runOnUiThread в context).
 */
internal class NetworkMonitor(
    private val context: Context,
    private val onOnlineChanged: (Boolean) -> Unit
) {

    private var callback: ConnectivityManager.NetworkCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun currentOnline(): Boolean = connectivityManager().activeNetwork != null

    fun start() {
        if (callback != null) return
        val manager = connectivityManager()
        onOnlineChanged(manager.activeNetwork != null)
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                notifyOnline(true)
            }

            override fun onLost(network: Network) {
                // При нескольких интерфейсах (eth0+wifi) сеть мог унаследовать другой активный.
                notifyOnline(manager.activeNetwork != null)
            }

            private fun notifyOnline(online: Boolean) {
                mainHandler.post { onOnlineChanged(online) }
            }
        }.also { manager.registerDefaultNetworkCallback(it) }
    }

    fun stop() {
        callback?.let {
            try {
                connectivityManager().unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w("NetworkMonitor", "network callback unregister: ${e.message}")
            }
        }
        callback = null
    }

    private fun connectivityManager(): ConnectivityManager = context.getSystemService(ConnectivityManager::class.java)
}
