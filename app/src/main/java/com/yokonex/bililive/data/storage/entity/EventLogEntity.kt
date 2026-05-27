package com.yokonex.bililive.data.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_logs")
data class EventLogEntity(
    @PrimaryKey val id: String,
    val eventType: String,
    val summary: String,
    val rawPayloadJson: String,
    val matchedRuleId: String?,
    val outputMode: String?,
    val outputSuccess: Boolean,
    val outputMessage: String?,
    val createdAt: Long,
)

