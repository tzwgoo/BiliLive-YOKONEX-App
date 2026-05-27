package com.yokonex.bililive

import android.app.Application

class BiliLiveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppServices.container = AppContainer(this)
    }
}
