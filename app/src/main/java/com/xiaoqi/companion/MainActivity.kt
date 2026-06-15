package com.xiaoqi.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.xiaoqi.companion.feature.onboarding.OnboardingScreen
import com.xiaoqi.companion.feature.onboarding.OnboardingViewModel
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
    const val Onboarding = "onboarding"
}

@Composable
private fun AuraAppNavHost(
    viewModel: ChatViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val slideIn = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(300),
    ) + fadeIn(animationSpec = tween(300))
    val slideOut = slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(300),
    ) + fadeOut(animationSpec = tween(300))
    val popSlideIn = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(300),
    ) + fadeIn(animationSpec = tween(300))
    val popSlideOut = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(300),
    ) + fadeOut(animationSpec = tween(300))

    NavHost(
        navController = navController,
        startDestination = AuraRoutes.Home,
        enterTransition = { slideIn },
        exitTransition = { slideOut },
        popEnterTransition = { popSlideIn },
        popExitTransition = { popSlideOut },
    ) {
        composable(
            route = AuraRoutes.Home,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
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
        composable(AuraRoutes.Onboarding) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(AuraRoutes.Home) {
                        popUpTo(AuraRoutes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
    }

    // 启动判断:未完成 onboarding → 跳 Onboarding(走一次性,避免堆栈污染)
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val isCompleted by onboardingViewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
    LaunchedEffect(isCompleted) {
        if (!isCompleted) {
            navController.navigate(AuraRoutes.Onboarding)
        }
    }
}
