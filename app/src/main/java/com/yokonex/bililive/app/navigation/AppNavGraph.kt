package com.yokonex.bililive.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    Surface(color = MaterialTheme.colorScheme.background) {
        NavHost(
            navController = navController,
            startDestination = "dashboard",
        ) {
            composable("dashboard") { Text("直播控制台") }
            composable("live") { Text("直播连接") }
            composable("output") { Text("输出配置") }
            composable("rules") { Text("规则配置") }
            composable("logs") { Text("事件日志") }
        }
    }
}

