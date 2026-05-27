package com.yokonex.bililive.data.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yokonex.bililive.data.storage.entity.WaveformEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaveformDao {
    @Query("SELECT * FROM waveforms ORDER BY name ASC")
    fun observeAll(): Flow<List<WaveformEntity>>

    @Query("SELECT COUNT(*) FROM waveforms")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(waveforms: List<WaveformEntity>)

    @Query("SELECT * FROM waveforms WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): WaveformEntity?
}

