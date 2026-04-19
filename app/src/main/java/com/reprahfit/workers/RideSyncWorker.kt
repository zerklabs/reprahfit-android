package com.reprahfit.workers

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reprahfit.CompletedRide
import com.reprahfit.HealthConnectRideExporter
import com.reprahfit.data.AppDatabase

class RideSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val exporter = HealthConnectRideExporter(applicationContext)

        if (exporter.availabilityStatus() != HealthConnectClient.SDK_AVAILABLE) {
            return Result.retry()
        }
        if (!exporter.hasRequiredRidePermissions()) {
            return Result.failure()
        }

        val dao = AppDatabase.getInstance(applicationContext).rideDao()
        val unsynced = dao.getUnsyncedRides()
        if (unsynced.isEmpty()) return Result.success()

        var allSynced = true
        for (entity in unsynced) {
            val ride = CompletedRide(
                startEpochMillis = entity.startTimeMillis,
                endEpochMillis = entity.endTimeMillis,
                distanceMeters = entity.distanceMeters,
                calories = entity.calories,
                averageHeartRate = entity.averageHeartRate,
                exerciseType = entity.exerciseType
            )
            try {
                exporter.exportRide(ride)
                dao.markSynced(entity.id)
            } catch (_: Exception) {
                allSynced = false
            }
        }

        return if (allSynced) Result.success() else Result.retry()
    }
}
