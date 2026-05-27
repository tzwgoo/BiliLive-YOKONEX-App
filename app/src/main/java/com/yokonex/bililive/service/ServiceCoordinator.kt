package com.yokonex.bililive.service

import com.yokonex.bililive.domain.usecase.MonitoringCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ServiceCoordinator : MonitoringCoordinator {
    private val _status = MutableStateFlow<ServiceStatus>(ServiceStatus.Idle)
    val status: StateFlow<ServiceStatus> = _status

    override suspend fun start() {
        _status.value = ServiceStatus.Starting
        _status.value = ServiceStatus.Running
    }

    override suspend fun stop() {
        _status.value = ServiceStatus.Stopping
        _status.value = ServiceStatus.Idle
    }

    fun notifyError(message: String) {
        _status.value = ServiceStatus.Error(message)
    }
}

sealed interface ServiceStatus {
    data object Idle : ServiceStatus
    data object Starting : ServiceStatus
    data object Running : ServiceStatus
    data object Reconnecting : ServiceStatus
    data object Stopping : ServiceStatus
    data class Error(val message: String) : ServiceStatus
}

