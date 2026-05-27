package com.yokonex.bililive.data.storage

import com.yokonex.bililive.data.mapper.WaveformMapper
import com.yokonex.bililive.data.storage.dao.WaveformDao

class StorageBootstrapper(
    private val settingsStore: SettingsStore,
    private val waveformDao: WaveformDao,
) {
    suspend fun seedDefaultsIfNeeded() {
        settingsStore.ensureDefaults()
        if (waveformDao.count() == 0) {
            waveformDao.insertAll(
                DefaultWaveforms.all.map(WaveformMapper::toEntity),
            )
        }
    }
}

