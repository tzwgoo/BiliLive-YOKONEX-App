package com.yokonex.bililive

import android.app.Application

class BiliLiveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppServices.applicationContext = applicationContext
        AppServices.container = AppContainer(this)
    }
}
