package com.reprahfit.workers

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reprahfit.HealthConnectRideExporter

class WeightRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val exporter = HealthConnectRideExporter(applicationContext)

        if (exporter.availabilityStatus() != HealthConnectClient.SDK_AVAILABLE) {
            return Result.retry()
        }
        if (!exporter.hasRequiredWeightPermissions()) {
            return Result.failure()
        }

        val pounds = exporter.readLatestWeightPounds() ?: return Result.success()
        val formatted = "%.1f".format(pounds)
        applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("weight", formatted)
            .apply()

        return Result.success()
    }
}
