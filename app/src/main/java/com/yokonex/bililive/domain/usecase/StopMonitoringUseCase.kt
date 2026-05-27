package com.yokonex.bililive.domain.usecase

class StopMonitoringUseCase(
    private val serviceCoordinator: MonitoringCoordinator,
) {
    suspend operator fun invoke() {
        serviceCoordinator.stop()
    }
}

