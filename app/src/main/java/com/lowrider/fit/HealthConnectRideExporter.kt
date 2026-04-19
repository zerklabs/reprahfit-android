package com.lowrider.fit

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import java.time.Instant
import java.time.ZoneOffset

const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

data class CompletedRide(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val distanceMeters: Double,
    val calories: Int,
    val averageHeartRate: Int = 0
)

class HealthConnectRideExporter(private val context: Context) {
    private val ridePermissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(DistanceRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class)
    )
    private val weightPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    fun availabilityStatus(): Int {
        return HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PROVIDER_PACKAGE)
    }

    fun requiredRidePermissions(): Set<String> = ridePermissions

    fun requiredWeightPermissions(): Set<String> = weightPermissions

    suspend fun hasRequiredRidePermissions(): Boolean {
        if (availabilityStatus() != HealthConnectClient.SDK_AVAILABLE) return false
        val granted = client().permissionController.getGrantedPermissions()
        return granted.containsAll(ridePermissions)
    }

    suspend fun hasRequiredWeightPermissions(): Boolean {
        if (availabilityStatus() != HealthConnectClient.SDK_AVAILABLE) return false
        val granted = client().permissionController.getGrantedPermissions()
        return granted.containsAll(weightPermissions)
    }

    suspend fun exportRide(ride: CompletedRide) {
        val startTime = Instant.ofEpochMilli(ride.startEpochMillis)
        val endTime = Instant.ofEpochMilli(ride.endEpochMillis)
        val startOffset = ZoneOffset.systemDefault().rules.getOffset(startTime)
        val endOffset = ZoneOffset.systemDefault().rules.getOffset(endTime)
        val metadata = Metadata.activelyRecorded(phoneDevice())

        val records = mutableListOf(
            ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                metadata = metadata,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                title = context.getString(R.string.health_connect_session_title),
                notes = context.getString(
                    R.string.health_connect_session_note,
                    "%.2f".format(ride.distanceMeters / 1609.344)
                )
            ),
            DistanceRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                distance = Length.meters(ride.distanceMeters),
                metadata = metadata
            ),
            TotalCaloriesBurnedRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                energy = Energy.kilocalories(ride.calories.toDouble()),
                metadata = metadata
            )
        )

        if (ride.averageHeartRate > 0) {
            records.add(
                HeartRateRecord(
                    startTime = startTime,
                    startZoneOffset = startOffset,
                    endTime = endTime,
                    endZoneOffset = endOffset,
                    metadata = metadata,
                    samples = listOf(
                        HeartRateRecord.Sample(
                            time = startTime,
                            beatsPerMinute = ride.averageHeartRate.toLong()
                        )
                    )
                )
            )
        }

        client().insertRecords(records)
    }

    suspend fun readLatestWeightPounds(): Double? {
        val response = client().readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(Instant.EPOCH, Instant.now()),
                ascendingOrder = false,
                pageSize = 1
            )
        )
        return response.records.firstOrNull()?.weight?.inPounds
    }

    private fun client(): HealthConnectClient {
        return HealthConnectClient.getOrCreate(context, HEALTH_CONNECT_PROVIDER_PACKAGE)
    }

    private fun phoneDevice(): Device {
        return Device(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            type = Device.TYPE_PHONE
        )
    }
}
