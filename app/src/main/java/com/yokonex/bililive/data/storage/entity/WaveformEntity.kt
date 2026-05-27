package com.yokonex.bililive.data.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waveforms")
data class WaveformEntity(
    @PrimaryKey val id: String,
    val name: String,
    val builtin: Boolean,
    val payloadJson: String,
)

