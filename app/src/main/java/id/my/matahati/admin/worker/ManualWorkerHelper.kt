package id.my.matahati.admin.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun enqueueManualSyncWorker(context: Context) {
    Log.d("ManualWorkerHelper", "enqueueManualSyncWorker() dipanggil")

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<SyncManualWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .addTag("sync_manual_absen")
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "sync_manual_absen",
        ExistingWorkPolicy.REPLACE, // 🔥 WAJIB
        workRequest
    )


    Log.d("ManualWorkerHelper", "✅ Worker sinkronisasi absen manual dijadwalkan.")
}
