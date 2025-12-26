// NetworkUtil.kt
package id.my.matahati.absensi.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

object NetworkUtils {

    // Cek koneksi internet
    fun isOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Listener untuk perubahan koneksi (Reactive)
    fun getNetworkStatusFlow(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Kirim status awal
        trySend(isOnline(context))

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    // ✅ Helper tambahan: Jalankan callback otomatis saat koneksi berubah
    fun observeNetwork(context: Context, onStatusChange: (Boolean) -> Unit) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            getNetworkStatusFlow(context).collect { isConnected ->
                onStatusChange(isConnected)
            }
        }
    }
}
