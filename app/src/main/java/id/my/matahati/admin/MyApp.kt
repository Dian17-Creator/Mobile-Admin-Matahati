package id.my.matahati.admin

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import id.my.matahati.admin.data.AppDatabase

class MyApp : Application(), Configuration.Provider {

    companion object {
        lateinit var DEVICE_ID: String
            private set

        lateinit var db: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // =========================
        // 📱 DEVICE ID (LOGIC LAMA — TIDAK DIUBAH)
        // =========================
        DEVICE_ID = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
        Log.d("DEVICE_ID", DEVICE_ID)

        // =========================
        // 🗄️ INIT DATABASE (BARU)
        // =========================
        db = AppDatabase.getDatabase(this)
        Log.d("MyApp", "✅ AppDatabase initialized")

        // =========================
        // ⚙️ WORKMANAGER (AMAN, OPTIONAL)
        // =========================
        try {
            WorkManager.initialize(
                this,
                workManagerConfiguration
            )
            Log.d("WorkManager", "✅ WorkManager initialized")
        } catch (e: IllegalStateException) {
            // Aman jika WorkManager sudah auto-init
            Log.w("WorkManager", "ℹ️ WorkManager already initialized")
        }
    }

    // =========================
    // ⚙️ KONFIGURASI WORKMANAGER
    // =========================
    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
    }
}
