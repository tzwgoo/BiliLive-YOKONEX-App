package com.yokonex.bililive.data.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yokonex.bililive.data.storage.dao.EventLogDao
import com.yokonex.bililive.data.storage.dao.RuleDao
import com.yokonex.bililive.data.storage.dao.WaveformDao
import com.yokonex.bililive.data.storage.entity.EventLogEntity
import com.yokonex.bililive.data.storage.entity.RuleEntity
import com.yokonex.bililive.data.storage.entity.WaveformEntity

@Database(
    entities = [
        RuleEntity::class,
        WaveformEntity::class,
        EventLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun waveformDao(): WaveformDao
    abstract fun eventLogDao(): EventLogDao
}

