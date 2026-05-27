package com.yokonex.bililive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yokonex.bililive.app.navigation.AppNavGraph
import com.yokonex.bililive.app.ui.theme.BiliLiveTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BiliLiveTheme {
                AppNavGraph()
            }
        }
    }
}

