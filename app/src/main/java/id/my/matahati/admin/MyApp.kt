package id.my.matahati.admin

import android.app.Application
import android.provider.Settings
import android.util.Log

class MyApp : Application() {

    companion object {
        lateinit var DEVICE_ID: String
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // ANDROID_ID = device_id resmi & legal
        DEVICE_ID = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // Log hanya untuk debug (tidak terlihat user biasa)
        Log.d("DEVICE_ID", DEVICE_ID)
    }
}
