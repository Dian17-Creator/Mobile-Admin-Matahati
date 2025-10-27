package id.my.matahati.admin.data

import android.content.Context

object Cleaner {

    // Fungsi utama untuk hapus cache
    fun clearAppCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            if (cacheDir.isDirectory) {
                cacheDir.deleteRecursively()
            }

            // Optional: hapus file sementara di filesDir
            val filesDir = context.filesDir
            if (filesDir.isDirectory) {
                filesDir.deleteRecursively()
            }

            // Optional: hapus database lokal kalau ada Room/SQLite
            // context.deleteDatabase("nama_database_kamu")

            println("✅ Cache dan file sementara berhasil dibersihkan")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Opsional: bersihkan SharedPreferences juga
    fun clearSharedPrefs(context: Context, prefName: String) {
        try {
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            println("✅ SharedPreferences '$prefName' berhasil dihapus")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
