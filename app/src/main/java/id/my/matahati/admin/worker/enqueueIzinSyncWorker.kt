package id.my.matahati.admin.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest

fun enqueueIzinSyncWorker(context: Context) {
    // ✅ Tambahkan constraint: worker hanya jalan kalau ada koneksi internet
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workRequest: WorkRequest = OneTimeWorkRequestBuilder<SyncIzinWorker>()
        .setConstraints(constraints)
        .addTag("sync_izin_worker")
        .build()

    WorkManager.getInstance(context).enqueue(workRequest)
}
