package com.reprahfit.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long,
    val distanceMeters: Double,
    val averageSpeedMph: Double,
    val calories: Int,
    @ColumnInfo(defaultValue = "0") val averageHeartRate: Int = 0,
    @ColumnInfo(defaultValue = "8") val exerciseType: Int = 8,
    @ColumnInfo(defaultValue = "0") val syncedToHealthConnect: Boolean = false
)
