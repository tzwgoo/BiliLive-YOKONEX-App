package com.yokonex.bililive.data.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean,
    val eventType: String,
    val cooldownSeconds: Int,
    val cooldownScope: String?,
    val conditionsJson: String,
    val actionBindingsJson: String,
)

