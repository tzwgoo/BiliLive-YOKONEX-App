package com.yokonex.bililive.data.live

import com.yokonex.bililive.domain.model.LiveEvent
import kotlinx.coroutines.flow.Flow

interface ThirdPartyLiveGateway {
    fun events(roomId: String): Flow<LiveEvent>
}

