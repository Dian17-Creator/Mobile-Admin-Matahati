package id.my.matahati.admin.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.my.matahati.admin.MyApp
import id.my.matahati.admin.SessionManager
import id.my.matahati.admin.ensureToken
import id.my.matahati.admin.utils.showSuccessNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SyncManualWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = MyApp.db.offlineManualAbsenDao()
            val list = dao.getAll()

            if (list.isEmpty()) {
                Log.d("SyncManualWorker", "📭 Tidak ada data offline")
                return@withContext Result.success()
            }

            // 🔐 pastikan token ada (tidak peduli hasilnya dulu)
            ensureToken(applicationContext)

            val session = SessionManager(applicationContext)
            val adminEmail = session.getUser()["email"] as? String
                ?: return@withContext Result.retry()

            val adminPassword =
                session.getPassword() ?: session.getTempPassword()
                ?: return@withContext Result.retry()

            val client = OkHttpClient()
            var hasFailure = false

            for (absen in list) {
                try {
                    val json = JSONObject().apply {
                        put("admin_email", adminEmail)
                        put("admin_password", adminPassword)
                        put("user_email", absen.userEmail)
                        put("user_password", absen.userPassword)
                        put("reason", absen.reason)
                        put("lat", absen.lat)
                        put("lng", absen.lng)

                        // 🔥 INI YANG HILANG
                        if (absen.placeName.isNotBlank()) {
                            put("cplacename", absen.placeName)
                        }

                        if (absen.photoBase64.isNotBlank()) {
                            put("photoBase64", absen.photoBase64)
                        }
                    }

                    val request = Request.Builder()
                        .url("https://absensi.matahati.my.id/manual_checkin.php")
                        .post(json.toString().toRequestBody("application/json".toMediaType()))
                        .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID)
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string().orEmpty()

                    val success =
                        response.isSuccessful && body.contains("\"status\":\"ok\"")

                    if (success) {
                        dao.delete(absen) // ✅ hapus HANYA jika sukses
                        Log.d("SyncManualWorker", "✅ Terkirim id=${absen.id}")
                    } else {
                        hasFailure = true
                        Log.e("SyncManualWorker", "❌ Gagal id=${absen.id} $body")
                    }

                } catch (e: Exception) {
                    hasFailure = true
                    Log.e("SyncManualWorker", "⚠️ Exception per item", e)
                }
            }

            // =========================
            // 📢 UI FEEDBACK (MAIN THREAD)
            // =========================
            if (!hasFailure) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "✅ Koneksi kembali. Absen manual berhasil dikirim.",
                        Toast.LENGTH_LONG
                    ).show()

                    // 🛰️ Broadcast ke Activity (kalau masih terbuka)
                    applicationContext.sendBroadcast(
                        Intent("SYNC_MANUAL_ABSEN_SUCCESS")
                    )
                }

                // 🔔 Notifikasi sistem (aman walau app di background)
                showSuccessNotification(applicationContext)

                return@withContext Result.success()
            }

            Result.retry()

        } catch (e: Exception) {
            Log.e("SyncManualWorker", "❌ Fatal error", e)
            Result.retry()
        }
    }
}