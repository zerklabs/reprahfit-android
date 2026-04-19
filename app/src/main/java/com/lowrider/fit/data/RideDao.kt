package com.lowrider.fit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert
    suspend fun insert(ride: RideEntity): Long

    @Query("SELECT * FROM rides ORDER BY startTimeMillis DESC")
    fun getAll(): Flow<List<RideEntity>>

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun deleteById(id: Long)
}
