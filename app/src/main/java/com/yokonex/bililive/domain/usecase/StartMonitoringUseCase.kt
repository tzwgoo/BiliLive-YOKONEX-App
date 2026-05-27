package com.yokonex.bililive.domain.usecase

class StartMonitoringUseCase(
    private val serviceCoordinator: MonitoringCoordinator,
) {
    suspend operator fun invoke() {
        serviceCoordinator.start()
    }
}

interface MonitoringCoordinator {
    suspend fun start()

    suspend fun stop()
}

