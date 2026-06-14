package com.xiaoqi.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xiaoqi.companion.feature.chat.AuraHomeScreen
import com.xiaoqi.companion.feature.chat.ChatScreen
import com.xiaoqi.companion.feature.chat.ChatViewModel
import com.xiaoqi.companion.feature.chat.McpSettingsScreen
import com.xiaoqi.companion.feature.chat.MemoryRoomScreen
import com.xiaoqi.companion.feature.chat.SettingsScreen
import com.xiaoqi.companion.ui.theme.CompanionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CompanionTheme {
                AuraAppNavHost(viewModel = chatViewModel)
            }
        }
    }
}

private object AuraRoutes {
    const val Home = "home"
    const val Chat = "chat"
    const val Settings = "settings"
    const val McpSettings = "settings/mcp"
    const val MemoryRoom = "memory-room"
}

@Composable
private fun AuraAppNavHost(
    viewModel: ChatViewModel,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AuraRoutes.Home,
    ) {
        composable(AuraRoutes.Home) {
            AuraHomeScreen(
                viewModel = viewModel,
                onOpenChat = { navController.navigate(AuraRoutes.Chat) },
                onOpenSettings = {
                    viewModel.prepareSettings()
                    navController.navigate(AuraRoutes.Settings)
                },
                onOpenMemoryRoom = { navController.navigate(AuraRoutes.MemoryRoom) },
                onOpenMcpSettings = {
                    viewModel.prepareMcpSettings()
                    navController.navigate(AuraRoutes.McpSettings)
                },
            )
        }
        composable(AuraRoutes.Chat) {
            ChatScreen(
                viewModel = viewModel,
                onOpenMemoryRoom = { navController.navigate(AuraRoutes.MemoryRoom) },
                onOpenSettings = {
                    viewModel.prepareSettings()
                    navController.navigate(AuraRoutes.Settings)
                },
                onOpenMcpSettings = {
                    viewModel.prepareMcpSettings()
                    navController.navigate(AuraRoutes.McpSettings)
                },
            )
        }
        composable(AuraRoutes.Settings) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMcpSettings = {
                    viewModel.prepareMcpSettings()
                    navController.navigate(AuraRoutes.McpSettings)
                },
            )
        }
        composable(AuraRoutes.McpSettings) {
            McpSettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AuraRoutes.MemoryRoom) {
            MemoryRoomScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
