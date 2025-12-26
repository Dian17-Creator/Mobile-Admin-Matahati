package id.my.matahati.admin.worker

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import id.my.matahati.admin.MyApp
import id.my.matahati.admin.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SyncManualWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = MyApp.db.offlineManualAbsenDao()
            val dataList = dao.getAll()

            if (dataList.isEmpty()) {
                Log.d("SyncManualWorker", "✅ Tidak ada data offline admin")
                return@withContext Result.success()
            }

            val session = SessionManager(applicationContext)
            val adminEmail = session.getUser()["email"] as? String ?: ""
            val adminPassword = session.getPassword() ?: session.getTempPassword() ?: ""

            val client = OkHttpClient()

            for (absen in dataList) {

                val json = JSONObject().apply {
                    put("admin_email", adminEmail)
                    put("admin_password", adminPassword)
                    put("user_email", absen.userEmail)
                    put("user_password", absen.userPassword)
                    put("reason", absen.reason)
                    put("lat", absen.lat)
                    put("lng", absen.lng)
                    put("photoBase64", absen.photoBase64)
                }

                val request = Request.Builder()
                    .url("https://absensi.matahati.my.id/manual_checkin.php")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Accept", "application/json")
                    .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                Log.d("SyncManualWorker", "Response: $body")

                if (response.isSuccessful && body.contains("\"status\":\"ok\"")) {
                    dao.delete(absen)
                    Log.d("SyncManualWorker", "🗑️ Data offline dihapus")
                } else {
                    Log.e("SyncManualWorker", "❌ Gagal sync: $body")
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "📤 Absen manual offline berhasil disinkronkan",
                    Toast.LENGTH_LONG
                ).show()

                applicationContext.sendBroadcast(
                    android.content.Intent("SYNC_MANUAL_ABSEN_SUCCESS")
                )
            }

            Result.success()

        } catch (e: Exception) {
            Log.e("SyncManualWorker", "❌ Error sync manual admin", e)
            Result.retry()
        }
    }
}
