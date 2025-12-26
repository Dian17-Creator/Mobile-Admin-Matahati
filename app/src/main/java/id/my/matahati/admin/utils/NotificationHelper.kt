package id.my.matahati.admin.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

fun showSuccessNotification(context: Context) {
    val channelId = "manual_absen_sync"
    val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Manual Absen Sync",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
        .setContentTitle("Absen Manual")
        .setContentText("✅ Koneksi kembali. Absen manual berhasil dikirim.")
        .setAutoCancel(true)
        .build()

    manager.notify(1001, notification)
}
