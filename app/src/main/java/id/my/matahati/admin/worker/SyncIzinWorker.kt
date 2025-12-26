package id.my.matahati.admin.worker

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.my.matahati.admin.MyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SyncIzinWorker(
    context: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = MyApp.db.offlineIzinDao()
            val list = dao.getAll()

            if (list.isEmpty()) {
                Log.d("SyncIzinWorker", "📭 Tidak ada data izin offline")
                return@withContext Result.success()
            }

            val client = OkHttpClient()
            var hasFailure = false

            for (izin in list) {
                try {
                    val json = JSONObject().apply {
                        put("userId", izin.userId)
                        put("adminId", izin.adminId)
                        put("requestDate", izin.date)
                        put("location", "${izin.lat},${izin.lng}")
                        put("placeName", izin.placeName)
                        put("category", izin.category)
                        put("reason", izin.reason)
                        put("photoBase64", izin.photoBase64)
                    }

                    val request = Request.Builder()
                        .url("https://absensi.matahati.my.id/izin_admin.php")
                        .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("Accept", "application/json")
                        .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID)
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string().orEmpty()

                    if (response.isSuccessful && body.contains("\"success\":true")) {
                        dao.deleteById(izin.id)
                        Log.d("SyncIzinWorker", "✅ Izin ${izin.id} berhasil dikirim")
                    } else {
                        hasFailure = true
                        Log.e("SyncIzinWorker", "❌ Gagal izin ${izin.id}: $body")
                    }

                } catch (e: Exception) {
                    hasFailure = true
                    Log.e("SyncIzinWorker", "⚠️ Error kirim izin ${izin.id}", e)
                }
            }

            // =========================
            // 📢 UI FEEDBACK (MAIN THREAD)
            // =========================
            if (!hasFailure) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "✅ Koneksi kembali. Izin berhasil dikirim.",
                        Toast.LENGTH_LONG
                    ).show()

                    // 🛰️ Broadcast ke Activity (kalau masih terbuka)
                    applicationContext.sendBroadcast(
                        Intent("SYNC_IZIN_ADMIN_SUCCESS")
                    )
                }

                return@withContext Result.success()
            }

            Result.retry()

        } catch (e: Exception) {
            Log.e("SyncIzinWorker", "🔥 Fatal error", e)
            Result.retry()
        }
    }
}